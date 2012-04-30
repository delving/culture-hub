package core.processing

import play.api.Play.current
import core.mapping.MappingService
import collection.JavaConverters._
import eu.delving.sip.MappingEngine
import core.indexing.{IndexingService, Indexing}
import core.SystemField
import core.Constants._
import com.mongodb.casbah.Imports._
import io.Source
import eu.delving.groovy.XmlSerializer
import models.{DataSet, MetadataRecord, RecordDefinition, DataSetState, Visibility}
import play.api.{Play, Logger}

/**
 * Processes a DataSet and all of its records so that it is available for publishing and
 * eventually indexed for search and visible in the Hub
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSetProcessor {

  type MultiMap = Map[String, List[Any]]

  val log = Logger("CultureHub")

  val AFF = "aff"

  private implicit def listMapToScala(map: java.util.Map[String, java.util.List[String]]) = map.asScala.map(v => (v._1, v._2.asScala.toList)).toMap

  def process(dataSet: DataSet) {
    val spec = dataSet.spec
    val orgId = dataSet.orgId

    val formats: List[RecordDefinition] = dataSet.getAllMappingFormats.flatMap(recDef => RecordDefinition.getRecordDefinition(recDef.prefix))

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

    val renderingFormat: Option[RecordDefinition] = if(formats.exists(_.prefix == AFF)) {
      formats.find(_.prefix == AFF)
    } else if(indexingFormat.isDefined) {
      indexingFormat
    } else {
      formats.headOption
    }



    // update processing state of DataSet
    DataSet.updateState(dataSet, DataSetState.PROCESSING)

    val now = System.currentTimeMillis()

    val formatsString = formats.map(_.prefix).mkString(", ")
    log.info("Starting processing of DataSet '%s': going to process formats '%s', format for indexing is %s".format(spec, formatsString, indexingFormat.map(_.prefix).getOrElse("NONE!")))

    // initialize context
    val processingFormats = formats.map(ProcessingFormat(_, dataSet))
    processingFormats.partition(!_.hasMapping)._1 foreach {
      f => log.warn("No mapping found for format %s, skipping its processing".format(f.prefix))
    }

    // drop previous index
    if (indexingFormat.isDefined) {
      IndexingService.deleteBySpec(orgId, spec)
    }

    // loop over records
    val recordsCollection = DataSet.getRecords(dataSet)
    val recordCount = recordsCollection.count(MongoDBObject())
    val records = recordsCollection.find(MongoDBObject())

    var indexedRecords: Int = 0

    try {
      for (record <- records) {
        if (DataSet.getState(orgId, spec) == DataSetState.PROCESSING) {

          // update state
          if (records.numSeen % 100 == 0) {
            DataSet.updateIndexingCount(dataSet, records.numSeen)
          }

          if (records.numSeen % 2000 == 0) {
            log.info("%s: processed %s of %s records, for formats '%s'".format(spec, records.numSeen, recordCount, formatsString))
          }

          for(format <- processingFormats; if(format.hasMapping && format.recordIsValid(record))) {
            val isIndexingFormat = indexingFormat.isDefined && indexingFormat.get.prefix == format.prefix
            val isRenderingFormat = renderingFormat.isDefined && renderingFormat.get.prefix == format.prefix

            val mainMappingResult = format.engine.execute(record.getRawXmlString)

            // cache mapping result
            DataSet.cacheMappedRecord(dataSet, record, format.prefix, MappingService.nodeTreeToXmlString(mainMappingResult.root()))

            // handle systemFields
            if(isRenderingFormat) {
              val systemFields = getSystemFields(mainMappingResult)
              val enrichedSystemFields = enrichSystemFields(systemFields, record.hubId, format.prefix)
              recordsCollection.update(MongoDBObject("_id" -> record._id), $set("systemFields" -> (enrichedSystemFields).asDBObject))
            }

            // if the current format is the to be indexed one, send the record out for indexing
            if (isIndexingFormat) {
              val fields: Map[String, List[Any]] = mainMappingResult.fields()
              val searchFields: Map[String, List[Any]] = mainMappingResult.searchFields()
              Indexing.indexOne(dataSet, record, fields ++ searchFields ++ getSystemFields(mainMappingResult), format.prefix)
              indexedRecords += 1
            }

            // also cache the result of possible crosswalks
            if (!format.crosswalkEngines.isEmpty) {
              val firstPassRecord = XmlSerializer.toXml(mainMappingResult.root())
              for (c <- format.crosswalkEngines) {
                val transformed = c._2.execute(firstPassRecord).root()
                DataSet.cacheMappedRecord(dataSet, record, c._1, MappingService.nodeTreeToXmlString(transformed))
              }
            }
          }
        }
      }

      if(indexingFormat.isDefined) {
        DataSet.updateIndexingCount(dataSet, indexedRecords)
      }

    } catch {
      case t => {
        t.printStackTrace()
        log.error("Error while processing records of DataSet %s".format(spec), t)
        DataSet.updateState(dataSet, DataSetState.ERROR)
      }
    }

    // finally, update the processing state again
    DataSet.getState(orgId, spec).name match {
      case DataSetState.PROCESSING.name =>
        if(indexingFormat.isDefined) {
          Indexing.commit()
        }
        DataSet.updateState(dataSet, DataSetState.ENABLED)
      case DataSetState.UPLOADED.name => // do nothing, this processing was cancelled
      case s@_ =>
        log.error("Failed to process DataSet %s: it is in state %s".format(spec, s))
        DataSet.updateState(dataSet, DataSetState.ERROR)
        if (indexingFormat.isDefined) {
          log.info("Deleting DataSet %s from SOLR".format(spec))
          IndexingService.deleteBySpec(orgId, spec)
        }
    }

    log.info("Processing of DataSet %s finished, took %s ms".format(spec, (System.currentTimeMillis() - now)))
  }

  private def getSystemFields(mappingResult: MappingEngine.Result) = {
    val systemFields: Map[String, List[Any]] = mappingResult.systemFields()
    val renamedSystemFields: Map[String, List[Any]] = systemFields.flatMap(sf => {
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
    val Array(orgId, spec, localRecordKey) = hubId.split("_")
    systemFields ++ Map(
      SPEC -> spec,
      RECORD_TYPE -> MDR,
      VISIBILITY -> Visibility.PUBLIC.value.toString,
      MIMETYPE -> "image/jpeg", // assume we have images, for the moment, since this is what most flat formats are anyway
      HAS_DIGITAL_OBJECT -> (systemFields.contains(THUMBNAIL) && systemFields.get(THUMBNAIL).size > 0),
      HUB_URI -> (if(currentFormatPrefix == AFF) "/%s/object/%s/%s".format(orgId, spec, localRecordKey) else "")
    ).map(v => (v._1, List(v._2))).toMap
  }

}

case class ProcessingFormat(format: RecordDefinition, dataSet: DataSet) {

  val prefix = format.prefix

  val javaNamespaces = (format.allNamespaces.map(ns => (ns.prefix -> ns.uri)).toMap[String, String] ++ dataSet.namespaces).asJava

  val hasMapping: Boolean = dataSet.mappings.contains(format.prefix) && dataSet.mappings(format.prefix).recordMapping.isDefined

  val engine: MappingEngine = new MappingEngine(dataSet.mappings(format.prefix).recordMapping.getOrElse(""), Play.classloader, MappingService.recDefModel, javaNamespaces)

  val crosswalkEngines = (RecordDefinition.getCrosswalkResources(format.prefix).map {
    r =>
      val targetPrefix = r.getPath.substring(r.getPath.indexOf(format.prefix + "-")).split("-")(0)
      (targetPrefix -> new MappingEngine(Source.fromURL(r).getLines().mkString("\n"), Play.classloader, MappingService.recDefModel, javaNamespaces))
    }).toMap[String, MappingEngine]

  def recordIsValid(record: MetadataRecord) = record.validOutputFormats.contains(format.prefix)

}
