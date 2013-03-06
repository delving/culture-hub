package processing

import play.api.Logger
import models._
import java.net.URL
import io.Source
import core.indexing.{ IndexingService, Indexing }
import core.{ HubId, HubServices }
import core.processing.{ DoProcess, ProcessingContext, CollectionProcessor, ProcessingSchema }
import akka.actor.{ Props, TypedProps, TypedActor }
import play.api.libs.concurrent.Akka
import play.api.Play.current

/**
 * TODO re-think the whole construct involving the typed actor interface. we might do well without it.
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait DataSetCollectionProcessor {

  def process(dataSet: DataSet, onSuccess: => Unit, onFailure: Throwable => Unit)(implicit configuration: OrganizationConfiguration)

}

object DataSetCollectionProcessor extends DataSetCollectionProcessor {

  val log = Logger("CultureHub")

  // TODO return a promise
  def process(dataSet: DataSet, onSuccess: => Unit, onFailure: Throwable => Unit)(implicit configuration: OrganizationConfiguration) {
    log.debug("About to start typed actor for DataSetCollectionProcessor")
    val processor: DataSetCollectionProcessor = TypedActor(Akka.system).typedActorOf(TypedProps[DataSetCollectionProcessorImpl])
    log.debug("We started the typed actor DataSetCollectionProcessor")
    var processing = true
    try {
      processor.process(
        dataSet,
        onSuccess = {
          onSuccess
          processing = false
        },
        onFailure = { t =>
          onFailure(t)
          processing = false
        })

      // TODO return promise and adapt client code
      while (processing) {
        Thread.sleep(500)
      }

    } catch {
      case t: Throwable =>
        log.error("Unexpected error during processing", t)
        DataSet.dao.updateState(dataSet, DataSetState.ERROR, errorMessage = Some(t.getMessage))
        processing = false
    } finally {
      TypedActor(Akka.system).stop(processor)
    }
  }

}

class DataSetCollectionProcessorImpl extends DataSetCollectionProcessor {

  val log = Logger("CultureHub")

  val RAW_PREFIX = "raw"
  val AFF_PREFIX = "aff"

  def process(dataSet: DataSet, onSuccess: => Unit, onFailure: Throwable => Unit)(implicit configuration: OrganizationConfiguration) {

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

    def indexOne(item: MetadataItem, fields: Map[String, List[String]], prefix: String)(implicit configuration: OrganizationConfiguration) =
      Indexing.indexOne(dataSet, HubId(item.itemId), fields, prefix)

    def onProcessingDone(context: ProcessingContext) {
      IndexingService.commit

      // we retry this one 3 times, in order to minimize the chances of loosing the whole index if a timeout happens to occur
      var retries = 0
      var success = false
      while (retries < 3 && !success) {
        try {
          IndexingService.deleteOrphansBySpec(dataSet.orgId, dataSet.spec, context.startProcessing)
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
      val collectionProcessor = TypedActor.context.actorOf(collectionProcessorProps)
      collectionProcessor ! DoProcess
    } catch {
      case t: Throwable =>
        onError(t)

    }

  }

}
