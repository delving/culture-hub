package core.processing

import core.storage.{BaseXStorage, Collection}
import scala.collection.JavaConverters._
import play.api.Logger
import core.mapping.MappingService
import akka.util.Duration
import java.util.concurrent.TimeUnit
import eu.delving.MappingResult
import core.SystemField
import core.Constants._
import scala.xml.Node
import core.indexing.IndexingService
import models._
import org.joda.time.{DateTimeZone, DateTime}

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
      implicit session => {
        val recordCount = BaseXStorage.count
        val records = BaseXStorage.findAllCurrent

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
                val recordIndex = (record \ "system" \ "index").text.toInt

                if (index % 100 == 0) updateCount(index)
                if (index % 2000 == 0) {
                  log.info("%s: processed %s of %s records, for schemas '%s'".format(collection.name, index, recordCount, targetSchemasString))
                }

                val directMappingResults: Map[String, MappingResult] = (for (targetSchema <- targetSchemas; if (targetSchema.isValidRecord(recordIndex) && targetSchema.sourceSchema == "raw")) yield {
                  val sourceRecord = (record \ "document" \ "input").toString()
                  (targetSchema.prefix -> targetSchema.engine.get.execute(sourceRecord))
                }).toMap

                val derivedMappingResults: Map[String, MappingResult] = (for (targetSchema <- targetSchemas; if (targetSchema.sourceSchema != "raw")) yield {
                  val sourceRecord = MappingService.nodeTreeToXmlString(directMappingResults(targetSchema.sourceSchema).root())
                  (targetSchema.prefix -> targetSchema.engine.get.execute(sourceRecord))
                }).toMap

                val mappingResults = directMappingResults ++ derivedMappingResults

                val allSystemFields = if (renderingSchema.isDefined && mappingResults.contains(renderingSchema.get.prefix)) {
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
                  systemFields = allSystemFields.getOrElse(Map.empty),
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
          log.info("%s: processed %s of %s records, for schemas '%s'".format(collection.name, index, recordCount, targetSchemasString))
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
