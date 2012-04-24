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
import models._
import core.SystemField

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

    // update processing state of DataSet
    DataSet.updateState(dataSet, DataSetState.PROCESSING)

    val now = System.currentTimeMillis()

    log.info("Starting processing of DataSet '%s': going to process formats '%s', format for indexing is %s".format(spec, formats.map(_.prefix).mkString(", "), indexingFormat.map(_.prefix).getOrElse("NONE!")))

    for (format <- formats) {

      val isIndexingFormat = indexingFormat.isDefined && indexingFormat.get.prefix == format.prefix

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
          if (isIndexingFormat) {
            IndexingService.deleteBySpec(orgId, spec)
          }

          // loop over records
          val recordsCollection = DataSet.getRecords(dataSet)
          val recordCount = recordsCollection.count(MongoDBObject("validOutputFormats" -> format.prefix))
          val records = recordsCollection.find(MongoDBObject("validOutputFormats" -> format.prefix))

          log.info("Processing %s valid records for format %s".format(recordCount, format.prefix))

          try {
            for (record <- records) {
              if (DataSet.getState(orgId, spec) == DataSetState.PROCESSING) {

                // update state
                if (records.numSeen % 100 == 0) {
                  DataSet.updateIndexingCount(dataSet, records.numSeen)
                }

                if (records.numSeen % 2000 == 0) {
                  log.info("%s: processed %s of %s records, for main format '%s' and crosswalks '%s'".format(spec, records.numSeen, recordCount, format.prefix, crosswalkEngines.keys.mkString(", ")))
                }

                val mainMappingResult = engine.execute(record.getRawXmlString)

                // cache mapping result
                DataSet.cacheMappedRecord(dataSet, record, format.prefix, MappingService.nodeTreeToXmlString(mainMappingResult.root()))

                // handle systemFields
                val systemFields = getSystemFields(mainMappingResult)
                val enrichedSystemFields = enrichSystemFields(systemFields, spec)
                recordsCollection.update(MongoDBObject("_id" -> record._id), $set("systemFields" -> (enrichedSystemFields).asDBObject))

                // if the current format is the to be indexed one, send the record out for indexing
                if (isIndexingFormat) {
                  val fields: Map[String, List[Any]] = mainMappingResult.fields()
                  val searchFields: Map[String, List[Any]] = mainMappingResult.searchFields()
                  Indexing.indexOne(dataSet, record, fields ++ searchFields ++ systemFields, format.prefix)
                }

                // also cache the result of possible crosswalks
                if (!crosswalkEngines.isEmpty) {
                  val firstPassRecord = XmlSerializer.toXml(mainMappingResult.root())
                  for (c <- crosswalkEngines) {
                    val transformed = c._2.execute(firstPassRecord).root()
                    DataSet.cacheMappedRecord(dataSet, record, c._1, MappingService.nodeTreeToXmlString(transformed))
                  }
                }
              }
            }
            if (isIndexingFormat) {
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
        DataSet.getState(orgId, spec).name match {
          case DataSetState.PROCESSING.name =>
            DataSet.updateState(dataSet, DataSetState.ENABLED)
            Indexing.commit()
          case DataSetState.UPLOADED.name => // do nothing
          case s@_ =>
            log.error("Failed to process DataSet %s: it is in state %s".format(spec, s))
            DataSet.updateState(dataSet, DataSetState.ERROR)
            if (isIndexingFormat) {
              log.info("Deleting DataSet %s from SOLR".format(spec))
              IndexingService.deleteBySpec(orgId, spec)
            }
        }
      }
    }

    log.info("Processing of DataSet %s finished, took %s ms".format(spec, (System.currentTimeMillis() - now)))
  }

  private def getSystemFields(mappingResult: MappingEngine.Result) = {
    val systemFields: Map[String, List[Any]] = mappingResult.systemFields()
    val renamedSystemFields: Map[String, List[Any]]  = systemFields.map(sf => {
      val name = try {
        SystemField.valueOf(sf._1).tag
      } catch {
        case _ => sf._1
      }
      (name -> sf._2)
    })
    renamedSystemFields
  }

  private def enrichSystemFields(systemFields: MultiMap, spec: String): MultiMap = {
    systemFields ++ Map(
      SPEC -> spec,
      RECORD_TYPE -> MDR,
      VISIBILITY -> Visibility.PUBLIC.value.toString,
      MIMETYPE -> "image/jpeg", // assume we have images, for the moment, since this is what most flat formats are anyway
      HAS_DIGITAL_OBJECT -> (systemFields.contains(THUMBNAIL) && systemFields.get(THUMBNAIL).size > 0)
    ).map(v => (v._1, List(v._2))).toMap
  }



}
