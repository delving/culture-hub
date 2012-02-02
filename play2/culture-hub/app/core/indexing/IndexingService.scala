package core.indexing

import com.mongodb.casbah.commons.TypeImports._
import models.{MetadataRecord, Thing}
import core.search.SolrServer

/**
 * Indexing API for Controllers
 */
object IndexingService extends SolrServer {

  /**
   * Indexes one Thing
   */
  def index(t: Thing) {
    stageForIndexing(t)
    getStreamingUpdateServer.commit()
  }

  /**
   * Stages one Thing for indexing
   */
  def stageForIndexing(t: Thing) {
    SolrServer.indexSolrDocument(t.toSolrDocument)
  }

  /**
   * Indexes one MDR
   */
  def index(mdr: MetadataRecord) {
    stageForIndexing(mdr)
    getStreamingUpdateServer.commit()
  }

  /**
   * Stages one MDR for indexing
   */
  def stageForIndexing(mdr: MetadataRecord) {
    val Array(orgId, spec, localRecordKey) = mdr.hubId.split("_")
    Indexing.indexOneInSolr(orgId, spec, mdr)
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

}

