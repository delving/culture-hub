package components

import com.mongodb.casbah.Imports._
import controllers.SolrServer
import eu.delving.sip.IndexDocument
import org.apache.solr.common.SolrInputDocument
import cake.ComponentRegistry
import org.apache.solr.client.solrj.response.UpdateResponse
import eu.delving.sip.MappingEngine
import scala.collection.JavaConversions.asJavaMap
import models._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Indexing extends SolrServer {

  @throws(classOf[MappingNotFoundException])
  @throws(classOf[SolrConnectionException])
  def indexInSolr(dataSet: DataSet, metadataFormatForIndexing: String): (Int, Int) = {
    val records = DataSet.getRecords(dataSet).find(MongoDBObject())
    DataSet.updateState(dataSet, DataSetState.INDEXING)
    val mapping = dataSet.mappings.get(metadataFormatForIndexing)
    if (mapping == None) throw new MappingNotFoundException("Unable to find mapping for " + metadataFormatForIndexing)
    val engine: MappingEngine = new MappingEngine(mapping.get.recordMapping.getOrElse(""), asJavaMap(dataSet.namespaces), play.Play.classloader.getParent, ComponentRegistry.metadataModel)
    var state = DataSet.getStateBySpecAndOrgId(dataSet.spec, dataSet.orgId)

    if (state == DataSetState.INDEXING) {
      records foreach {
        record =>
          if (records.numSeen % 100 == 0) {
            DataSet.updateIndexingCount(dataSet, records.numSeen)
            state = DataSet.getStateBySpecAndOrgId(dataSet.spec, dataSet.orgId)
          }
          val mapping = engine.executeMapping(record.getXmlString())

          mapping match {
            case indexDoc: IndexDocument => {
              val doc = createSolrInputDocument(indexDoc)
              addDelvingHouseKeepingFields(doc, dataSet, record, metadataFormatForIndexing)
              try {
                getStreamingUpdateServer.add(doc)
              } catch {
                case t: Throwable => throw new SolrConnectionException("Unable to add document to Solr", t)
              }
            }
            case _ => // catching null
          }
      }
    }

    state match {
      case DataSetState.INDEXING =>
        DataSet.updateState(dataSet, DataSetState.ENABLED)
        getStreamingUpdateServer.commit()
      case _ =>
        //        getStreamingUpdateServer.rollback() // todo find out what this does
        println("deleting dataset from solr " + dataSet.spec)
        deleteFromSolr(dataSet)
    }
    println(engine.toString)
    (1, 0)
  }


  def deleteFromSolr(dataSet: DataSet) {
    val deleteResponse: UpdateResponse = getStreamingUpdateServer.deleteByQuery("delving_spec:" + dataSet.spec)
    deleteResponse.getStatus
    getStreamingUpdateServer.commit
  }

  private def createSolrInputDocument(indexDoc: IndexDocument): SolrInputDocument = {
    import scala.collection.JavaConversions._

    val doc = new SolrInputDocument
    indexDoc.getMap.entrySet().foreach {
      entry =>
        val unMungedKey = entry.getKey // todo later unmunge the key with namespaces.replaceAll("_", ":")
        entry.getValue.foreach(
          value =>
            doc.addField(unMungedKey, value.toString)
        )
    }
    doc
  }

  def addDelvingHouseKeepingFields(inputDoc: SolrInputDocument, dataSet: DataSet, record: MetadataRecord, format: String) {
    import scala.collection.JavaConversions._
    import controllers.search.SolrBindingService

    inputDoc.addField("delving_pmhId", "%s_%s".format(dataSet.spec, record._id.toString))
    inputDoc.addField("delving_spec", "%s".format(dataSet.spec))
    inputDoc.addField("delving_currentFormat", format)
    inputDoc.addField("delving_recordType", "dataset")
    inputDoc.addField("delving_visibility_single", dataSet.visibility.value)
    val hubId = "%s_%s_%s".format(dataSet.orgId, dataSet.spec, record.localRecordKey)
    inputDoc.addField("delving_hubId", hubId)
    val indexedKeys = inputDoc.keys.map(key => (SolrBindingService.stripDynamicFieldLabels(key), key)).toMap // to filter always index a facet with _facet .filter(!_.matches(".*_(s|string|link|single)$"))
    // add facets at indexing time
    dataSet.idxFacets.foreach {
      facet =>
        if (indexedKeys.contains(facet)) {
          val facetContent = inputDoc.get(indexedKeys.get(facet).get).getValues
          inputDoc addField("%s_facet".format(facet), facetContent)
        }
    }
    // adding sort fields at index time
    dataSet.idxSortFields.foreach {
      sort =>
        if (indexedKeys.contains(sort)) {
          inputDoc addField("sort_all_%s".format(sort), inputDoc.get(indexedKeys.get(sort).get))
        }
    }
    
    if (inputDoc.containsKey("id")) inputDoc.remove("id")
    inputDoc.addField("id", hubId)

    dataSet.getMetadataFormats(true).foreach(format => inputDoc.addField("delving_publicFormats", format.prefix))
    dataSet.getMetadataFormats(false).foreach(format => inputDoc.addField("delving_allFormats", format.prefix))

    // todo add more elements: hasDigitalObject. etc
  }

}