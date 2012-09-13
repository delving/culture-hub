package core.indexing

import core.search.{SolrBindingService, SolrServer}
import core.Constants._
import core.SystemField._
import core.indexing.IndexField._
import play.api.Logger
import org.apache.solr.common.SolrInputDocument
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.apache.solr.client.solrj.SolrQuery
import models.{Visibility, DomainConfiguration}

/**
 * Indexing API
 *
 * TODO turn into a real service and inject via subcut
 */
object IndexingService extends SolrServer {

  /**
   * Stages a SOLR InputDocument for indexing, and applies all generic delving mechanisms on top
   */
  def stageForIndexing(doc: SolrInputDocument)(implicit configuration: DomainConfiguration) {
    import scala.collection.JavaConversions._

    val hasDigitalObject: Boolean = !doc.entrySet().filter(entry => entry.getKey.startsWith(THUMBNAIL.tag) && !entry.getValue.isEmpty).isEmpty
    if (doc.containsKey(HAS_DIGITAL_OBJECT.key)) doc.remove(HAS_DIGITAL_OBJECT.key)
    doc.addField(HAS_DIGITAL_OBJECT.key, hasDigitalObject)

    if (hasDigitalObject) doc.setDocumentBoost(1.4.toFloat)

    if (!doc.containsKey(VISIBILITY.key)) {
      doc += (VISIBILITY -> Visibility.PUBLIC.value.toString)
    }

    // add full text from digital objects
    val fullTextUrl = "%s_string".format(FULL_TEXT_OBJECT_URL.key)
    if (doc.containsKey(fullTextUrl)) {
      val pdfUrl = doc.get(fullTextUrl).getFirstValue.toString
      if (pdfUrl.endsWith(".pdf")) {
        val fullText = TikaIndexer.getFullTextFromRemoteURL(pdfUrl)
        doc.addField("%s_text".format(FULL_TEXT.key), fullText)
      }
    }


    // configured facets

    // to filter always index a facet with _facet .filter(!_.matches(".*_(s|string|link|single)$"))
    val indexedKeys: Map[String, String] = doc.keys.map(key => (SolrBindingService.stripDynamicFieldLabels(key), key)).toMap

    // add facets at indexing time
    configuration.getFacets.foreach {
      facet =>
        if (indexedKeys.contains(facet.facetName)) {
          val facetContent = doc.get(indexedKeys.get(facet.facetName).get).getValues
          doc.addField("%s_facet".format(facet.facetName), facetContent)
          // enable case-insensitive autocomplete
          doc.addField("%s_lowercase".format(facet.facetName), facetContent)
        }
    }

    // adding sort fields at index time
    configuration.getSortFields.foreach {
      sort =>
        if (indexedKeys.contains(sort.sortKey)) {
          doc.addField("sort_all_%s".format(sort.sortKey), doc.get(indexedKeys.get(sort.sortKey).get))
        }
    }

    // add standard facets
    if(!doc.containsKey(RECORD_TYPE.key + "_facet")) {
      doc.addField(RECORD_TYPE.key + "_facet", doc.getField(RECORD_TYPE.key).getFirstValue)
    }
    if(!doc.containsKey(HAS_DIGITAL_OBJECT.key + "_facet")) {
      doc.addField(HAS_DIGITAL_OBJECT.key + "_facet", hasDigitalObject)
    }

    getStreamingUpdateServer(configuration).add(doc)
  }

  /**
   * Commits staged Things or MDRs to index
    */
  def commit(implicit configuration: DomainConfiguration) = {
    getStreamingUpdateServer(configuration).commit()
  }

  /**
   * Rolls back staged indexing requests
   */
  def rollback(implicit configuration: DomainConfiguration) {
    getStreamingUpdateServer(configuration).rollback()
  }

  /**
   * Deletes from the index by string ID
   */
  def deleteById(id: String)(implicit configuration: DomainConfiguration) {
    getStreamingUpdateServer(configuration).deleteById(id)
    commit
  }

  /**
   * Deletes from the index by query
   */
  def deleteByQuery(query: String)(implicit configuration: DomainConfiguration) {
    SolrServer.deleteFromSolrByQuery(query)
    commit
  }

  /**
   * Deletes from the index by collection spec
   */
  def deleteBySpec(orgId: String, spec: String)(implicit configuration: DomainConfiguration) {
    val deleteQuery = SPEC.tag + ":" + spec + " " + ORG_ID.key + ":" + orgId
    Logger.info("Deleting dataset from Solr Index: %s".format(deleteQuery))
    val deleteResponse = getStreamingUpdateServer(configuration).deleteByQuery(deleteQuery)
    deleteResponse.getStatus
    commit
  }

  def deleteOrphansBySpec(orgId: String, spec: String, startIndexing: DateTime)(implicit configuration: DomainConfiguration) {
    val fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    val deleteQuery = SPEC.tag + ":" + spec + " AND " + ORG_ID.key + ":" + orgId + " AND timestamp:[* TO " + fmt.print(startIndexing.minusSeconds(15)) + "]"
    val orphans = getSolrServer(configuration).query(new SolrQuery(deleteQuery)).getResults.getNumFound
    if (orphans > 0) {
      try {
        val deleteResponse = getStreamingUpdateServer(configuration).deleteByQuery(deleteQuery)
        deleteResponse.getStatus
        commit
        Logger.info("Deleting orphans %s from dataset from Solr Index: %s".format(orphans.toString, deleteQuery))
      }
      catch {
        case e: Exception => Logger.info("Unable to remove orphans for %s because of %s".format(spec, e.getMessage))
      }
    }
    else
      Logger.info("No orphans found for dataset in Solr Index: %s".format(deleteQuery))

  }

}

