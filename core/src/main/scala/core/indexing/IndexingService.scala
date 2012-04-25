package core.indexing

import com.mongodb.casbah.commons.TypeImports._
import core.search.SolrServer
import core.Constants._
import play.api.Logger
import org.apache.solr.common.SolrInputDocument

/**
 * Indexing API for Controllers
 */
object IndexingService extends SolrServer {

  /**
   * Stages a SOLR InputDocument for indexing
   */
  def stageForIndexing(doc: SolrInputDocument) {
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
    SolrServer.deleteFromSolrById(id)
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
   * Deletes a List of ObjectIds
   */
  def deleteById(ids: List[ObjectId]) {
    import scala.collection.JavaConversions._
    getStreamingUpdateServer.deleteById(ids.map(_.toString))
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

