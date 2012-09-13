package core.processing

import scala.collection.JavaConverters._
import play.api.Logger
import core.mapping.MappingService
import akka.util.Duration
import java.util.concurrent.TimeUnit
import eu.delving.MappingResult
import core.{HubId, SystemField}
import core.SystemField._
import core.Constants._
import core.indexing.IndexField._
import core.collection.Collection
import core.storage.BaseXStorage
import core.indexing.IndexingService
import models._
import org.joda.time.{DateTimeZone, DateTime}
import xml.{Elem, NodeSeq, Node}

/**
 * CollectionProcessor, essentially taking care of:
 *
 * - iterating over all records
 * - running the primary mappings and derived ones for each valid record, for each selected target schema
 * - extracting system fields for the given renderingSchema and together with the serialized result caching them in the MetadataCache
 * - indexing the record in the selected indexingSchema
 *
 */
class CollectionProcessor(collection: Collection,
                          targetSchemas: List[ProcessingSchema],
                          indexingSchema: Option[ProcessingSchema],
                          renderingSchema: Option[ProcessingSchema],
                          basexStorage: BaseXStorage) {

  val log = Logger("CultureHub")

  type MultiMap = Map[String, List[String]]

  private implicit def listMapToScala(map: java.util.Map[String, java.util.List[String]]) = map.asScala.map(v => (v._1, v._2.asScala.toList)).toMap

  class ChildSelectable(ns: NodeSeq) {
    def \* = ns flatMap { _ match {
      case e: Elem => e.child
      case _ => NodeSeq.Empty
    } }
  }

  implicit def nodeSeqIsChildSelectable(xml: NodeSeq) = new ChildSelectable(xml)

  def process(interrupted: => Boolean,
              updateCount: Long => Unit,
              onError: Throwable => Unit,
              indexOne: (MetadataItem, MultiMap, String) => Either[Throwable, String],
              onIndexingComplete: DateTime => Unit
             )(implicit configuration: DomainConfiguration) {

    val startProcessing: DateTime = new DateTime(DateTimeZone.UTC)
    val targetSchemasString = targetSchemas.map(_.prefix).mkString(", ")
    log.info("Starting processing of collection '%s': going to process schemas '%s', schema for indexing is '%s', format for rendering is '%s'".format(
      collection.spec, targetSchemasString, indexingSchema.map(_.prefix).getOrElse("NONE!"), renderingSchema.map(_.prefix).getOrElse("NONE!"))
    )

    try {
      basexStorage.withSession(collection) {
        implicit session => {
          var record: Node = null
          var index: Int = 0

          updateCount(0)

          try {
            val recordCount = basexStorage.count
            val records = basexStorage.findAllCurrent
            val cache = MetadataCache.get(collection.getOwner, collection.spec, collection.itemType)

            records.zipWithIndex.foreach {
              r => {
                if (!interrupted) {
                  record = r._1
                  index = r._2

                  val localId = (record \ "@id").text
                  val hubId = "%s_%s_%s".format(collection.getOwner, collection.spec, localId)
                  val recordIndex = (record \ "system" \ "index").text.toInt
                  val modulo = math.round(recordCount / 100)

                  if (index % (if(modulo == 0) 100 else modulo) == 0) updateCount(index)
                  if (index % 2000 == 0) {
                    log.info("%s:%s: processed %s of %s records, for schemas '%s'".format(collection.getOwner, collection.spec, index, recordCount, targetSchemasString))
                  }

                  val directMappingResults: Map[String, MappingResult] = (for (targetSchema <- targetSchemas; if (targetSchema.isValidRecord(recordIndex) && targetSchema.sourceSchema == "raw")) yield {
                    val sourceRecord = (record \ "document" \ "input" \*).mkString("\n")
                    try {
                      (targetSchema.prefix -> targetSchema.engine.get.execute(sourceRecord))
                    } catch {
                      case t: Throwable => {
                        log.error(
                          """While processing source input document:
                            |
                            |%s
                            |
                          """.stripMargin.format(sourceRecord), t)
                        throw t
                      }
                    }
                  }).toMap

                  val derivedMappingResults: Map[String, MappingResult] = (for (targetSchema <- targetSchemas; if (targetSchema.sourceSchema != "raw")) yield {
                    val sourceRecord = MappingService.nodeTreeToXmlString(directMappingResults(targetSchema.sourceSchema).root(), true)
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

                  val serializedRecords = mappingResults.flatMap {
                    r => {
                      try {
                        val serialized = MappingService.nodeTreeToXmlString(r._2.rootAugmented(), r._1 != "raw")
                        Some((r._1 -> serialized))
                      } catch {
                        case t: Throwable => {
                          log.error(
                            """While attempting to serialize the following output document:
                              |
                              |%s
                              |
                            """.stripMargin.format(r._2.root()), t)
                          throw t
                          None
                        }
                      }
                    }
                  }

                  val mappingResultSchemaVersions: Map[String, String] = mappingResults.keys.
                          flatMap(schemaPrefix => targetSchemas.find(_.prefix == schemaPrefix)).
                          map(processingSchema => (processingSchema.definition.prefix -> processingSchema.definition.schemaVersion)).
                          toMap

                  val cachedRecord = MetadataItem(
                    collection = collection.spec,
                    itemType = collection.itemType.itemType,
                    itemId = hubId,
                    xml = serializedRecords,
                    schemaVersions = mappingResultSchemaVersions,
                    systemFields = allSystemFields.getOrElse(Map.empty),
                    index = index
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
            log.info("%s: processed %s of %s records, for schemas '%s'".format(
              collection.spec, index, recordCount, targetSchemasString)
            )

            if (!interrupted && indexingSchema.isDefined) {
              onIndexingComplete(startProcessing)
            }

            if(!interrupted) {
              updateCount(index)
              log.info("Processing of collection %s of organization %s finished, took %s seconds".format(
                collection.spec, collection.getOwner, Duration(System.currentTimeMillis() - startProcessing.toDate.getTime, TimeUnit.MILLISECONDS).toSeconds)
              )
            } else {
              updateCount(0)
              if (indexingSchema.isDefined) {
                log.info("Deleting DataSet %s from SOLR".format(collection.spec))
                IndexingService.deleteBySpec(collection.getOwner, collection.spec)
              }
              log.info("Processing of collection %s of organization %s interrupted after %s seconds".format(
                collection.spec, collection.getOwner, Duration(System.currentTimeMillis() - startProcessing.toDate.getTime, TimeUnit.MILLISECONDS).toSeconds)
              )
            }

          } catch {
            case t: Throwable => {
              t.printStackTrace()

              log.error("""Error while processing records of collection %s of organization %s, at index %s
              |
              | Source record:
              |
              | %s
              |
              """.stripMargin.format(collection.spec, collection.getOwner, index, record), t)

              if (indexingSchema.isDefined) {
                log.info("Deleting DataSet %s from SOLR".format(collection.spec))
                IndexingService.deleteBySpec(collection.getOwner, collection.spec)
              }

              updateCount(0)
              log.error("Error while processing collection %s of organization %s: %s".format(collection.spec, collection.getOwner, t.getMessage), t)
              onError(t)
            }

          }
        }

      }
    } catch {
      case t: Throwable => {
        t.printStackTrace()
        log.error("Error while processing collection %s of organization %s, cannot read source data: %s".format(collection.spec, collection.getOwner, t.getMessage), t)
        onError(t)
      }

    }

  }


  private def getSystemFields(mappingResult: MappingResult): MultiMap = {
    val systemFields: Map[String, List[String]] = mappingResult.systemFields().asScala.map(f => (f._1.getLocalPart -> f._2.asScala.toList)).toMap[String, List[String]]
    val renamedSystemFields: Map[String, List[String]] = systemFields.flatMap(sf => {
      try {
        Some(SystemField.valueOf(sf._1).tag -> sf._2)
      } catch {
        case t: Throwable =>
          // we boldly ignore any fields that do not match the system fields
          None
      }
    })
    renamedSystemFields
  }

  private def enrichSystemFields(systemFields: MultiMap, hubId: String, currentFormatPrefix: String): MultiMap = {
    val id = HubId(hubId)
    systemFields ++ Map(
      SPEC.tag -> id.spec,
      HAS_DIGITAL_OBJECT.key -> (systemFields.contains(SystemField.THUMBNAIL.tag) && systemFields.get(SystemField.THUMBNAIL.tag).size > 0).toString
    ).map(v => (v._1, List(v._2))).toMap
  }


}
