package core.indexing

import com.mongodb.casbah.commons.TypeImports._
import core.search.SolrServer
import core.Constants._
import play.api.Logger
import org.apache.solr.common.SolrInputDocument

/**
 * Indexing API
 */
object IndexingService extends SolrServer {

  /**
   * Stages a SOLR InputDocument for indexing, and applies all generic delving mechanisms on top
   */
  def stageForIndexing(doc: SolrInputDocument) {
    import scala.collection.JavaConversions._

    val hasDigitalObject: Boolean = !doc.entrySet().filter(entry => entry.getKey.startsWith(THUMBNAIL) && !entry.getValue.isEmpty).isEmpty
    if (doc.containsKey(HAS_DIGITAL_OBJECT)) doc.remove(HAS_DIGITAL_OBJECT)
    doc.addField(HAS_DIGITAL_OBJECT, hasDigitalObject)

    if (hasDigitalObject) doc.setDocumentBoost(1.4.toFloat)

    if (!doc.containsKey(VISIBILITY)) {
      doc addField(VISIBILITY, "10") // set to public by default
    }

    // standard facets
    doc.addField(RECORD_TYPE + "_facet", doc.getField(RECORD_TYPE).getFirstValue)
    doc.addField(HAS_DIGITAL_OBJECT + "_facet", hasDigitalObject)

    getStreamingUpdateServer.add(doc)
  }

  /**
   * Commits staged Things or MDRs to index
    */
  def commit() {
    getStreamingUpdateServer.commit()
  }

  /**
   * Rolls back staged indexing requests
   */
  def rollback() {
    getStreamingUpdateServer.rollback()
  }

  /**
   * Deletes from the index by string ID
   */
  def deleteById(id: String) {
    getStreamingUpdateServer.deleteById(id)
    commit()
  }

  /**
   * Deletes from the index by ObjectId
   */
  def deleteById(id: ObjectId) {
    SolrServer.deleteFromSolrById(id)
    commit()
  }

  /**
   * Deletes from the index by query
   */
  def deleteByQuery(query: String) {
    SolrServer.deleteFromSolrByQuery(query)
    commit()
  }

  /**
   * Deletes from the index by collection spec
   */
  def deleteBySpec(orgId: String, spec: String) {
    val deleteQuery = SPEC + ":" + spec + " " + ORG_ID + ":" + orgId
    Logger.info("Deleting dataset from Solr Index: %s".format(deleteQuery))
    val deleteResponse = getStreamingUpdateServer.deleteByQuery(deleteQuery)
    deleteResponse.getStatus
    getStreamingUpdateServer.commit
  }

}

