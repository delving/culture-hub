package core.processing

import play.api.Play.current
import core.mapping.MappingService
import collection.JavaConverters._
import eu.delving.sip.MappingEngine
import core.indexing.{IndexingService, Indexing}
import play.api.{Logger, Play}
import util.Constants._
import com.mongodb.casbah.Imports._
import io.Source
import eu.delving.groovy.XmlSerializer
import collection.mutable.{MultiMap, HashMap}
import org.w3c.dom.Node
import models._
import play.libs.XPath

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
    val spec = dataSet.spec
    val orgId = dataSet.orgId

    val formats: List[RecordDefinition] = dataSet.getAllMappingFormats

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

    val summaryFormat: Option[RecordDefinition] = if (indexingFormat.isDefined) {
      indexingFormat
    } else {
      formats.headOption
    }

    // update processing state of DataSet
    DataSet.updateState(dataSet, DataSetState.PROCESSING)

    val now = System.currentTimeMillis()

    log.info("Starting processing of DataSet '%s': going to process formats '%s', format for indexing is %s".format(spec, formats.map(_.prefix).mkString(", "), indexingFormat.map(_.prefix).getOrElse("NONE!")))

    for (format <- formats) {

      if (DataSet.getState(orgId, spec) == DataSetState.PROCESSING) {
        val mapping = dataSet.mappings(format.prefix)

        // TODO re-introduce later
        // find all user objects that use records as their thumbnail. we need this in case the thumbnail URL changed
        //    val thumbnailLinks: Map[String, List[Link]] = Link.find(MongoDBObject("linkType" -> Link.LinkType.THUMBNAIL)).toList.groupBy(_.to.hubAlternativeId.get).toMap

        val javaNamespaces = (format.allNamespaces.map(ns => (ns.prefix -> ns.uri)).toMap[String, String] ++ dataSet.namespaces).asJava

        // bring mapping engine to life
        if (mapping.recordMapping.isDefined) {
          val engine: MappingEngine = new MappingEngine(mapping.recordMapping.getOrElse(""), Play.classloader, MappingService.recDefModel, javaNamespaces)

          // if there are crosswalks from the target format to another format, we for the moment will process them automatically
          val crosswalkEngines = (RecordDefinition.getCrosswalkResources(format.prefix).map {
            r =>
              val targetPrefix = r.getPath.substring(r.getPath.indexOf(format.prefix + "-")).split("-")(0)
              (targetPrefix -> new MappingEngine(Source.fromURL(r).getLines().mkString("\n"), Play.classloader, MappingService.recDefModel, javaNamespaces))
          }).toMap[String, MappingEngine]

          // drop previous index
          if (indexingFormat.isDefined && indexingFormat.get.prefix == format.prefix) {
            IndexingService.deleteBySpec(orgId, spec)
          }

          // loop over records
          val recordsCollection = DataSet.getRecords(dataSet)
          val recordCount = recordsCollection.count(MongoDBObject("validOutputFormats" -> format.prefix))
          val records = recordsCollection.find(MongoDBObject("validOutputFormats" -> format.prefix))

          log.info("Processing %s valid records for format %s".format(recordCount, format.prefix))

          try {
            for (record <- records) {

              // update state
              if (records.numSeen % 100 == 0) {
                DataSet.updateIndexingCount(dataSet, records.numSeen)
              }

              if (records.numSeen % 2000 == 0) {
                log.info("%s: processed %s of %s records, for main format '%s' and crosswalks '%s'".format(spec, records.numSeen, recordCount, format.prefix, crosswalkEngines.keys.mkString(", ")))
              }

              val mainMappingResult = engine.toNode(record.getRawXmlString)

              // cache mapping result
              DataSet.cacheMappedRecord(dataSet, record, format.prefix, MappingService.nodeTreeToXmlString(mainMappingResult))

              // extract summary fields
              val summaryFields = summaryFormat.map {
                sf => extractSummaryFields(sf, mainMappingResult, javaNamespaces)
              }

              // cache summary fields in mongo for the hub
              if (summaryFields.isDefined) {
                val mappedSummaryFields = summaryFields.get

                // set SummaryFields required by the hub, result of processing
                val hubSummaryFields = Map(
                  SPEC -> spec,
                  RECORD_TYPE -> MDR,
                  VISIBILITY -> Visibility.PUBLIC.value,
                  MIMETYPE -> "image/jpeg", // assume we have images, for the moment, since this is what most flat formats are anyway
                  HAS_DIGITAL_OBJECT -> (mappedSummaryFields.get(THUMBNAIL).isDefined && mappedSummaryFields.get(THUMBNAIL).get.size > 0 && mappedSummaryFields.get(THUMBNAIL).get.head.length > 0)
                )

                // use only the first value for the moment
                val singleMappedSummaryFields = mappedSummaryFields.map(f => (f._1, f._2.headOption.getOrElse("")))

                recordsCollection.update(MongoDBObject("_id" -> record._id), $set("summaryFields" -> (singleMappedSummaryFields ++ hubSummaryFields).asDBObject))
              }

              // if the current format is the to be indexed one, send the record out for indexing
              if (indexingFormat == Some(format)) {

                val indexDocument = engine.toIndexDocument(record.getRawXmlString)

                // append summary fields
                if (summaryFields.isDefined) {
                  summaryFields.get.foreach {
                    sf => sf._2.foreach {
                      value => indexDocument.put(sf._1, value)
                    }
                  }
                }

                // append search fields
                val searchFields = extractSearchFields(format, mainMappingResult, javaNamespaces)
                searchFields.foreach {
                  sf => sf._2.foreach {
                    value => indexDocument.put(sf._1, value)
                  }
                }

                Indexing.indexOne(dataSet, record, Some(indexDocument), format.prefix)

              }


              // also cache the result of possible crosswalks
              if (!crosswalkEngines.isEmpty) {
                val firstPassRecord = XmlSerializer.toXml(engine.toNode(record.getRawXmlString))
                for (c <- crosswalkEngines) {
                  val transformed = c._2.toNode(firstPassRecord)
                  DataSet.cacheMappedRecord(dataSet, record, c._1, XmlSerializer.toXml(transformed))
                }
              }
            }

            if (indexingFormat == Some(format)) {
              DataSet.updateIndexingCount(dataSet, records.numSeen)
            }
            log.info("%s: processed %s of %s records, for main format '%s' and crosswalks '%s'".format(spec, records.numSeen, recordCount, format.prefix, crosswalkEngines.keys.mkString(", ")))

          } catch {
            case t => {
              t.printStackTrace()
              log.error("Error while processing records for format %s of DataSet %s".format(format.prefix, spec), t)
              DataSet.updateState(dataSet, DataSetState.ERROR)
            }
          }
        } else {
          log.warn("No mapping found for format %s, skipping its processing".format(format.prefix))
        }

        // finally, update the processing state again
        DataSet.getState(spec, orgId).name match {
          case DataSetState.PROCESSING.name =>
            DataSet.updateState(dataSet, DataSetState.ENABLED)
            Indexing.commit()
          case s@_ =>
            log.error("Failed to process DataSet %s: it is in state %s".format(spec, s))
            DataSet.updateState(dataSet, DataSetState.ERROR)
            if (indexingFormat == Some(format.prefix)) {
              log.info("Deleting DataSet %s from SOLR".format(spec))
              IndexingService.deleteBySpec(orgId, spec)
            }
        }
      }
    }

    log.info("Processing of DataSet %s finished, took %s ms".format(spec, (System.currentTimeMillis() - now)))
  }

  private def extractSummaryFields(sf: RecordDefinition, node: Node, javaNamespaces: java.util.Map[String, String]) = {
    val extractedSummaryFields = new HashMap[String, collection.mutable.Set[String]]() with MultiMap[String, String]
    for (summaryField: SummaryField <- sf.summaryFields.filter(_.isValid)) {
      val value = XPath.selectText(summaryField.xpath, node, javaNamespaces)
      extractedSummaryFields.put(summaryField.tag, value)
    }
    extractedSummaryFields
  }

  private def extractSearchFields(sf: RecordDefinition, node: Node, javaNamespaces: java.util.Map[String, String]) = {
    val extractedSearchFields = new HashMap[String, collection.mutable.Set[String]]() with MultiMap[String, String]
    for (searchField: SearchField <- sf.searchFields) {
      val value = XPath.selectText(searchField.xpath, node, javaNamespaces)
      extractedSearchFields.put(searchField.name + "_" + searchField.dataType, value)
    }
    extractedSearchFields
  }


}
