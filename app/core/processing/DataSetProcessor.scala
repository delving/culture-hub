package core.processing

import play.api.Play.current
import core.mapping.MappingService
import collection.JavaConverters._
import eu.delving.sip.{IndexDocument, MappingEngine}
import org.w3c.dom.Node
import core.indexing.{IndexingService, Indexing}
import play.api.{Logger, Play}
import util.Constants._
import com.mongodb.casbah.Imports._
import io.Source
import eu.delving.groovy.XmlSerializer
import models.{Visibility, DataSetState, RecordDefinition, DataSet}

/**
 * Processes a DataSet and all of its records so that it is available for publishing and
 * eventually indexed for search and visible in the Hub
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSetProcessor {

  val log = Logger("CultureHub")

  val AFF = "aff"

  val summaryFields = List(TITLE, DESCRIPTION, OWNER, CREATOR, THUMBNAIL, LANDING_PAGE, DEEP_ZOOM_URL, PROVIDER, SPEC)

  def process(dataSet: DataSet) {

    val formats = dataSet.getPublishableMappingFormats

    val flatIndexingMapping: Option[RecordDefinition] = formats.find(f => Some(f.prefix) == dataSet.getIndexingMappingPrefix && f.isFlat)

    val indexingFormat: Option[RecordDefinition] = if (formats.exists(_.prefix == AFF)) {
      // we have AFF, hence indexing via AFF is available. We use it instead of the selected one, since we don't
      // support multiple indexes.
      Some(dataSet.mappings(AFF).format)
    } else if (flatIndexingMapping.isDefined) {
      flatIndexingMapping
    } else {
      None
    }

    val now = System.currentTimeMillis()

    log.info("Starting processing of DataSet '%s': going to process formats '%s', format for indexing is %s".format(dataSet.spec, formats.map(_.prefix).mkString(", "), indexingFormat.map(_.prefix).getOrElse("NONE!")))

    formats foreach {
      format =>

        val mapping = dataSet.mappings(format.prefix)

        // TODO re-introduce later
        // find all user objects that use records as their thumbnail. we need this in case the thumbnail URL changed
        //    val thumbnailLinks: Map[String, List[Link]] = Link.find(MongoDBObject("linkType" -> Link.LinkType.THUMBNAIL)).toList.groupBy(_.to.hubAlternativeId.get).toMap

        val javaNamespaces = format.allNamespaces.map(ns => (ns.prefix -> ns.uri)).toMap[String, String].asJava

        // bring mapping engine to life
        val engine: MappingEngine = new MappingEngine(mapping.recordMapping.getOrElse(""), Play.classloader, MappingService.recDefModel, javaNamespaces)

        // if there are crosswalks from the target format to another format, we for the moment will process them automatically
        val crosswalkEngines = (RecordDefinition.getCrosswalkFiles(format.prefix).map {
          f =>
            val targetPrefix = f.getName.split("-")(1)
            (targetPrefix -> new MappingEngine(Source.fromFile(f).getLines().mkString("\n"), Play.classloader, MappingService.recDefModel, javaNamespaces))
        }).toMap[String, MappingEngine]

        // update processing state of DataSet
        DataSet.updateStateAndProcessingCount(dataSet, DataSetState.PROCESSING)

        // retrieve records
        val recordsCollection = DataSet.getRecords(dataSet)
        val records = recordsCollection.find(MongoDBObject("validOutputFormats" -> format.prefix))

        var state = DataSet.getStateBySpecAndOrgId(dataSet.spec, dataSet.orgId)

        // drop previous index
        if (indexingFormat == Some(format)) {
          IndexingService.deleteBySpec(dataSet.orgId, dataSet.spec)
        }


        // loop over records
        val recordCount = recordsCollection.count(MongoDBObject("validOutputFormats" -> format.prefix))
        log.info("Processing %s valid records for format %s".format(recordCount, format.prefix))

        try {
          records foreach {
            record => {

              // update state
              if (records.numSeen % 100 == 0) {
                DataSet.updateIndexingCount(dataSet, records.numSeen)
                state = DataSet.getStateBySpecAndOrgId(dataSet.spec, dataSet.orgId)
              }

              if(records.numSeen % 2000 == 0) {
                log.info("%s: processed %s of %s records, for main format '%s' and crosswalks '%s'".format(dataSet.spec, records.numSeen, recordCount, format.prefix, crosswalkEngines.keys.mkString(", ")))
              }

              val mainMappingResult = if (format.isFlat) {
                MappingResult(engine.toIndexDocument(record.getRawXmlString))
              } else {
                MappingResult(engine.toNode(record.getRawXmlString))
              }

              // cache mapping result
              DataSet.cacheMappedRecord(dataSet, record, format.prefix, mainMappingResult.xmlString)

              // if the current format is the to be indexed one, send the record out for indexing
              if (indexingFormat == Some(format) && mainMappingResult.isFlatDocument) {
                Indexing.indexOne(dataSet, record, mainMappingResult.indexDocument, indexingFormat.get.prefix)
              }

              // if this is a flat record definition, try to get some summary fields for the hub to show something
              if (mainMappingResult.isFlatDocument) {
                val indexDocument = mainMappingResult.indexDocument.get.getMap.asScala

                val mappedSummaryFields = (for (field <- summaryFields) yield {
                  val value = indexDocument.get(field)
                  val summaryFieldValue: String = if (value.isDefined) {
                    if (value.get.isEmpty) "" else value.get.get(0).toString
                  } else {
                    ""
                  }
                  (field -> summaryFieldValue)
                }).toMap[String, String]

                // set SummaryFields required by the hub, result of processing
                val hubSummaryFields = Map(
                  SPEC -> dataSet.spec,
                  RECORD_TYPE -> MDR,
                  VISIBILITY -> Visibility.PUBLIC.value,
                  MIMETYPE -> "image/jpeg", // assume we have images, for the moment, since this is what most flat formats are anyway
                  HAS_DIGITAL_OBJECT -> (indexDocument.get(THUMBNAIL).isDefined && indexDocument.get(THUMBNAIL).get.size() > 0 && indexDocument.get(THUMBNAIL).get.get(0).toString.length() > 0)
                )

                recordsCollection.update(MongoDBObject("_id" -> record._id), $set("summaryFields" -> (mappedSummaryFields ++ hubSummaryFields).asDBObject))
              }

              // also cache the result of possible crosswalks
              if(!crosswalkEngines.isEmpty) {
                val firstPassRecord = XmlSerializer.toXml(engine.toNode(record.getRawXmlString))
                for(c <- crosswalkEngines) {
                  val transformed = c._2.toNode(firstPassRecord)
                  DataSet.cacheMappedRecord(dataSet, record, c._1, XmlSerializer.toXml(transformed))
                }
              }

            }
          }
        } catch {
          case t =>
            t.printStackTrace()
            log.error("Error during processing of DataSet %s".format(dataSet.spec), t)
            DataSet.updateState(dataSet, DataSetState.ERROR)


        }
        // finally, update the processing state again
        state match {
          case DataSetState.PROCESSING =>
            log.info("%s: processed %s of %s records, for main format '%s' and crosswalks '%s'".format(dataSet.spec, records.numSeen, recordCount, format.prefix, crosswalkEngines.keys.mkString(", ")))
            DataSet.updateState(dataSet, DataSetState.ENABLED)
            Indexing.commit()
          case _ =>
            log.error("Failed to process DataSet %s".format(dataSet.spec))
            if(indexingFormat == Some(format.prefix)) {
              log.info("Deleting DataSet %s from SOLR".format(dataSet.spec))
              IndexingService.deleteBySpec(dataSet.orgId, dataSet.spec)
            }
        }
    }
    log.info("Processing of DataSet %s finished, took %s ms".format(dataSet.spec, (System.currentTimeMillis() - now)))

  }

  case class MappingResult(xmlString: String, indexDocument: Option[IndexDocument], nodeTree: Option[Node]) {
    def isFlatDocument = indexDocument.isDefined
  }

  object MappingResult {

    def apply(indexDocument: IndexDocument): MappingResult = MappingResult(MappingService.indexDocumentToXmlString(indexDocument), Some(indexDocument), None)

    def apply(nodeTree: Node): MappingResult = MappingResult(MappingService.nodeTreeToXmlString(nodeTree), None, Some(nodeTree))
  }


}
