package core.processing

import eu.delving.basex.client._
import core.storage.{BaseXStorage, Collection}
import scala.collection.JavaConverters._
import play.api.{Play, Logger}
import core.mapping.MappingService
import java.net.URL
import scala.io.Source
import akka.util.Duration
import java.util.concurrent.TimeUnit
import eu.delving.{MappingResult, MappingEngine}
import core.SystemField
import core.Constants._
import scala.xml.Node
import core.indexing.{Indexing, IndexingService}
import play.api.Play.current
import models._
import org.joda.time.{DateTimeZone, DateTime}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSetCollectionProcessor {

  val log = Logger("CultureHub")

  val RAW_PREFIX = "raw"
  val AFF_PREFIX = "aff"

  def process(dataSet: DataSet) {

    val selectedSchemas: List[RecordDefinition] = dataSet.getAllMappingFormats.flatMap(recDef => RecordDefinition.getRecordDefinition(recDef.prefix))

    val selectedProcessingSchemas: Seq[ProcessingSchema] = selectedSchemas map {
      t => new ProcessingSchema {
        val definition: RecordDefinition = t
        val namespaces: Map[String, String] = t.getNamespaces ++ dataSet.namespaces
        val mapping: Option[String] = if (dataSet.mappings.contains(t.prefix) && dataSet.mappings(t.prefix) != null) dataSet.mappings(t.prefix).recordMapping else None
        val sourceSchema: String = RAW_PREFIX
      }
    }

    val crosswalks: Seq[(RecordDefinition, URL)] = selectedSchemas.map(source => (source -> RecordDefinition.getCrosswalkResources(source.prefix))).flatMap(cw => cw._2.map(c => (cw._1, c)))
    val crosswalkSchemas: Seq[ProcessingSchema] = crosswalks flatMap {
      c =>
        val prefix = c._2.getPath.substring(c._2.getPath.indexOf(c._1.prefix + "-")).split("-")(0)
        val recordDefinition = RecordDefinition.getRecordDefinition(prefix)

        if (recordDefinition.isDefined) {
          val schema = new ProcessingSchema {
            val definition = recordDefinition.get
            val namespaces: Map[String, String] = c._1.getNamespaces ++ dataSet.namespaces
            val mapping: Option[String] = Some(Source.fromURL(c._2).getLines().mkString("\n"))
            val sourceSchema: String = c._1.prefix
          }
          Some(schema)
        } else {
          log.warn("Could not find RecordDefinition for schema '%s', which is the target of crosswalk '%s' - skipping it.".format(prefix, c._2))
          None
        }
    }

    val targetSchemas: List[ProcessingSchema] = selectedProcessingSchemas.toList ++ crosswalkSchemas.toList

    val actionableTargetSchemas = targetSchemas.partition(_.hasMapping)._1
    val incompleteTargetSchemas = targetSchemas.partition(_.hasMapping)._2

    if (!incompleteTargetSchemas.isEmpty) {
      log.warn("Could not find mapping for the following schemas: %s. They will be ignored in the mapping process.".format(incompleteTargetSchemas.mkString(", ")))
    }

    val indexingSchema: Option[ProcessingSchema] = dataSet.idxMappings.headOption.flatMap(i => actionableTargetSchemas.find(_.prefix == i))

    val renderingSchema: Option[ProcessingSchema] = if (actionableTargetSchemas.exists(_.prefix == AFF_PREFIX)) {
      actionableTargetSchemas.find(_.prefix == AFF_PREFIX)
    } else if (indexingSchema.isDefined) {
      indexingSchema
    } else {
      actionableTargetSchemas.headOption
    }

    val collectionProcessor = new CollectionProcessor(Collection(dataSet.orgId, dataSet.spec), actionableTargetSchemas, indexingSchema, renderingSchema)
    def interrupted = DataSet.getState(dataSet.orgId, dataSet.spec) != DataSetState.PROCESSING
    def updateCount(count: Long) {
      DataSet.updateIndexingCount(dataSet, count)
    }
    def onError(t: Throwable) {
      DataSet.updateState(dataSet, DataSetState.ERROR, Some(t.getMessage))
    }
    def indexOne(item: MetadataItem, fields: CollectionProcessor#MultiMap, prefix: String) = Indexing.indexOne(dataSet, item, fields, prefix)

    DataSet.updateState(dataSet, DataSetState.PROCESSING)
    collectionProcessor.process(interrupted, updateCount, onError, indexOne)
    DataSet.updateState(dataSet, DataSetState.ENABLED)
  }


}

abstract class ProcessingSchema {

  val definition: RecordDefinition
  val namespaces: Map[String, String]
  val mapping: Option[String]
  val sourceSchema: String

  lazy val prefix = definition.prefix
  lazy val hasMapping = mapping.isDefined
  lazy val javaNamespaces = namespaces.asJava
  lazy val engine: Option[MappingEngine] = mapping.map(new MappingEngine(_, Play.classloader, MappingService.recDefModel, javaNamespaces))

  def isValidRecord(record: Node) = !(record \\ "invalidTargetSchemas").text.split(",").contains(prefix)

}

class CollectionProcessor(collection: Collection, targetSchemas: List[ProcessingSchema], indexingSchema: Option[ProcessingSchema], renderingSchema: Option[ProcessingSchema]) {

  val log = Logger("CultureHub")

  type MultiMap = Map[String, List[String]]

  private implicit def listMapToScala(map: java.util.Map[String, java.util.List[String]]) = map.asScala.map(v => (v._1, v._2.asScala.toList)).toMap

  def process(interrupted: => Boolean, updateCount: Long => Unit, onError: Throwable => Unit, indexOne: (MetadataItem, MultiMap, String) => Either[Throwable, String]) {
    val now = System.currentTimeMillis()
    val startIndexing: DateTime = new DateTime(DateTimeZone.UTC)
    val targetSchemasString = targetSchemas.map(_.prefix).mkString(", ")
    log.info("Starting processing of collection '%s': going to process schemas '%s', schema for indexing is '%s', format for rendering is '%s'".format(collection.name, targetSchemasString, indexingSchema.map(_.prefix).getOrElse("NONE!"), renderingSchema.map(_.prefix).getOrElse("NONE!")))

    BaseXStorage.withSession(collection) {
      session => {
        val currentVersion = session.findOne("let $r := /record return <currentVersion>{max($r/@version)}</currentVersion>").get.text.toInt
        val recordCount = session.findOne("let $r := /record where $r/@version = %s return <count>{count($r)}</count>".format(currentVersion)).get.text.toInt
        val records = session.find("for $i in /record where $i/@version = %s order by $i/system/index return $i".format(currentVersion))

        val cache = MetadataCache.get(collection.orgId, collection.name, ITEM_TYPE_MDR)

        var record: Node = null
        var index: Int = 0

        try {
          records.zipWithIndex.foreach {
            r => {
              if (!interrupted) {
                record = r._1
                index = r._2

                val localId = (record \ "@id").text
                val hubId = "%s_%s_%s".format(collection.orgId, collection.name, localId)

                if (index % 100 == 0) updateCount(index)
                if (index % 2000 == 0) {
                  log.info("%s: processed %s of %s records, for schemas '%s'".format(collection.name, index, recordCount, targetSchemasString))
                }

                val directMappingResults: Map[String, MappingResult] = (for (targetSchema <- targetSchemas; if (targetSchema.isValidRecord(record) && targetSchema.sourceSchema == "raw")) yield {
                  val sourceRecord = (record \ "document" \ "input").toString()
                  (targetSchema.prefix -> targetSchema.engine.get.execute(sourceRecord))
                }).toMap

                val derivedMappingResults: Map[String, MappingResult] = (for (targetSchema <- targetSchemas; if (targetSchema.isValidRecord(record) && targetSchema.sourceSchema != "raw")) yield {
                  val sourceRecord = MappingService.nodeTreeToXmlString(directMappingResults(targetSchema.sourceSchema).root())
                  (targetSchema.prefix -> targetSchema.engine.get.execute(sourceRecord))
                }).toMap

                val mappingResults = directMappingResults ++ derivedMappingResults

                val systemFields = if (renderingSchema.isDefined && mappingResults.contains(renderingSchema.get.prefix)) {
                  val systemFields = getSystemFields(mappingResults(renderingSchema.get.prefix))
                  val enriched = enrichSystemFields(systemFields, hubId, renderingSchema.get.prefix)
                  Some(enriched)
                } else {
                  None
                }

                val cachedRecord = MetadataItem(
                  collection = collection.name,
                  itemType = ITEM_TYPE_MDR,
                  itemId = hubId,
                  xml = mappingResults.map(r => (r._1 -> MappingService.nodeTreeToXmlString(r._2.root()))),
                  systemFields = systemFields.getOrElse(Map.empty),
                  index = index.toInt
                )
                cache.saveOrUpdate(cachedRecord)

                if (indexingSchema.isDefined && mappingResults.contains(indexingSchema.get.prefix)) {
                  val r = mappingResults(indexingSchema.get.prefix)
                  val fields: Map[String, List[String]] = r.fields()
                  val searchFields: Map[String, List[String]] = r.searchFields()
                  indexOne(cachedRecord, fields ++ searchFields ++ getSystemFields(r), indexingSchema.get.prefix)
                }

              }
            }
          }

          if (!interrupted && indexingSchema.isDefined) {
            IndexingService.commit()
            IndexingService.deleteOrphansBySpec(collection.orgId, collection.name, startIndexing)
          }

        } catch {
          case t =>
            t.printStackTrace()

            log.error("""Error while processing records of collection %s, at index %s
            |
            | Source record:
            |
            | %s
            |
            """.stripMargin.format(collection.name, index, record), t)

            if (indexingSchema.isDefined) {
              log.info("Deleting DataSet %s from SOLR".format(collection.name))
              IndexingService.deleteBySpec(collection.orgId, collection.name)
            }

            onError(t)
        }

        updateCount(index)
        log.info("Processing of collection %s finished, took %s seconds".format(collection.name, Duration(System.currentTimeMillis() - now, TimeUnit.MILLISECONDS).toSeconds))
      }

    }

  }


  private def getSystemFields(mappingResult: MappingResult): MultiMap = {
    val systemFields: Map[String, List[String]] = mappingResult.systemFields()
    val renamedSystemFields: Map[String, List[String]] = systemFields.flatMap(sf => {
      try {
        Some(SystemField.valueOf(sf._1).tag -> sf._2)
      } catch {
        case _ =>
          // we boldly ignore any fields that do not match the system fields
          None
      }
    })
    renamedSystemFields
  }

  private def enrichSystemFields(systemFields: MultiMap, hubId: String, currentFormatPrefix: String): MultiMap = {
    val HubId(orgId, spec, localRecordKey) = hubId
    systemFields ++ Map(
      SPEC -> spec,
      RECORD_TYPE -> MDR,
      VISIBILITY -> Visibility.PUBLIC.value.toString,
      MIMETYPE -> "image/jpeg", // assume we have images, for the moment, since this is what most flat formats are anyway
      HAS_DIGITAL_OBJECT -> (systemFields.contains(THUMBNAIL) && systemFields.get(THUMBNAIL).size > 0).toString,
      HUB_URI -> (if (currentFormatPrefix == "aff") "/%s/object/%s/%s".format(orgId, spec, localRecordKey) else "")
    ).map(v => (v._1, List(v._2))).toMap
  }


}
