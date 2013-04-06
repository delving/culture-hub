package processing

import play.api.Logger
import models._
import java.net.URL
import io.Source
import core._
import core.processing.{ DoProcess, CollectionProcessor, ProcessingSchema }
import akka.actor.{ Actor, Props }
import indexing.Indexing
import processing.ProcessingContext

/**
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class DataSetCollectionProcessor extends Actor {

  val log = Logger("CultureHub")

  lazy val indexingServiceLocator: DomainServiceLocator[IndexingService] = HubModule.inject[DomainServiceLocator[IndexingService]](name = None)

  val RAW_PREFIX = "raw"
  val AFF_PREFIX = "aff"

  def receive = {
    case ProcessDataSetCollection(set, onSuccess, onFailure, configuration) => process(set, onSuccess, onFailure)(configuration)
  }

  def process(dataSet: DataSet, onSuccess: () => Unit, onFailure: Throwable => Unit)(implicit configuration: OrganizationConfiguration) {

    log.info(s"Starting DataSet collection processing for set ${dataSet.spec}")

    val invalidRecords = DataSet.dao.getInvalidRecords(dataSet)

    val selectedSchemas: Seq[RecordDefinition] = dataSet.mappings.flatMap(mapping => RecordDefinition.getRecordDefinition(mapping._2.schemaPrefix, mapping._2.schemaVersion)).toSeq

    val selectedProcessingSchemas: Seq[ProcessingSchema] = selectedSchemas map {
      t =>
        new ProcessingSchema {
          val definition: RecordDefinition = t
          val namespaces: Map[String, String] = t.getNamespaces ++ dataSet.getNamespaces
          val mapping: Option[String] = if (dataSet.mappings.contains(t.prefix) && dataSet.mappings(t.prefix) != null) dataSet.mappings(t.prefix).recordMapping else None
          val sourceSchema: String = RAW_PREFIX

          def isValidRecord(index: Int): Boolean = definition.prefix == RAW_PREFIX || !invalidRecords(t.prefix).contains(index)
        }
    }

    val crosswalks: Seq[(RecordDefinition, URL)] = selectedSchemas.map(source => (source -> RecordDefinition.getCrosswalkResources(source.prefix))).flatMap(cw => cw._2.map(c => (cw._1, c)))
    val crosswalkSchemas: Seq[ProcessingSchema] = crosswalks flatMap {
      c =>
        val prefix = c._2.getPath.substring(c._2.getPath.indexOf(c._1.prefix + "-")).split("-")(0)
        val recordDefinition = RecordDefinition.getRecordDefinition(prefix, "1.0.0") // TODO versions for crosswalks

        if (recordDefinition.isDefined) {
          val schema = new ProcessingSchema {
            val definition = recordDefinition.get
            val namespaces: Map[String, String] = c._1.getNamespaces ++ dataSet.getNamespaces
            val mapping: Option[String] = Some(Source.fromURL(c._2).getLines().mkString("\n"))
            val sourceSchema: String = c._1.prefix

            def isValidRecord(index: Int): Boolean = true // TODO later we need to figure out a way to handle validation for crosswalks
          }
          Some(schema)
        } else {
          log.warn("Could not find RecordDefinition for schema '%s', which is the target of crosswalk '%s' - skipping it.".format(prefix, c._2))
          None
        }
    }

    val targetSchemas: List[ProcessingSchema] = selectedProcessingSchemas.toList ++ crosswalkSchemas.toList

    val isActionable: ProcessingSchema => Boolean = s => s.hasMapping || s.definition.prefix == RAW_PREFIX

    val actionableTargetSchemas = targetSchemas.partition(isActionable)._1
    val incompleteTargetSchemas = targetSchemas.partition(isActionable)._2

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

    def interrupted = {
      val current = DataSet.dao.getState(dataSet.orgId, dataSet.spec)
      current != DataSetState.PROCESSING && current != DataSetState.QUEUED
    }

    def updateCount(count: Long) {
      DataSet.dao.updateIndexingCount(dataSet, count)
    }

    def onError(t: Throwable) {
      DataSet.dao.updateState(dataSet, DataSetState.ERROR, None, Some(t.getMessage))
    }

    def indexOne(itemId: HubId, fields: Map[String, List[String]], prefix: String)(implicit configuration: OrganizationConfiguration) =
      Indexing.indexOne(dataSet, itemId, fields, prefix)

    def onProcessingDone(context: ProcessingContext) {
      indexingServiceLocator.byDomain.commit

      // we retry this one 3 times, in order to minimize the chances of loosing the whole index if a timeout happens to occur
      var retries = 0
      var success = false
      while (retries < 3 && !success) {
        try {
          indexingServiceLocator.byDomain.deleteOrphansBySpec(dataSet.orgId, dataSet.spec, context.startProcessing)
          success = true
        } catch {
          case t: Throwable => retries += 1
        }
      }
      if (!success) {
        log.error("Could not delete orphans records from SOLR. You may have to clean up by hand.")
      }
    }

    // TODO refactor using ProcessingContext
    val collectionProcessorProps = Props(new CollectionProcessor(
      dataSet,
      dataSet.getNamespaces,
      actionableTargetSchemas,
      indexingSchema,
      renderingSchema,
      interrupted,
      updateCount,
      onError,
      indexOne,
      onProcessingDone,
      onSuccess,
      HubServices.basexStorages.getResource(configuration)
    ))

    try {
      val collectionProcessor = context.actorOf(collectionProcessorProps)
      collectionProcessor ! DoProcess
    } catch {
      case t: Throwable =>
        onError(t)

    }

  }

}

case class ProcessDataSetCollection(set: DataSet, onSuccess: () => Unit, onFailure: Throwable => Unit, configuration: OrganizationConfiguration)
