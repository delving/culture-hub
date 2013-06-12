package services

import core.SystemField._
import core.indexing.IndexField._
import play.api.Logger
import org.apache.solr.common.{ SolrInputField, SolrInputDocument }
import models.{ Visibility, OrganizationConfiguration }
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.apache.solr.client.solrj.SolrQuery
import extensions.HTTPClient
import java.io.InputStream
import org.apache.tika.sax.BodyContentHandler
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.pdf.PDFParser
import org.apache.tika.parser.ParseContext
import core.IndexingService
import search.{ SolrServer, SolrBindingService }
import scala.concurrent.{ Await, Future }
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._

/**
 * Indexing API
 *
 * TODO turn into a real service and inject via subcut
 */
class SOLRIndexingService extends SolrServer with IndexingService {
  val log = Logger("CultureHub")

  /**
   * Stages a document for indexing, and applies all generic delving mechanisms on top
   */
  def stageForIndexing(doc: IndexDocument)(implicit configuration: OrganizationConfiguration) {
    val solrDoc = new SolrInputDocument()
    doc foreach { pair =>
      pair._2 foreach { value =>
        solrDoc.addField(pair._1, value)
      }
    }

    stageForIndexing(solrDoc)
  }

  /**
   * Stages a SOLR InputDocument for indexing, and applies all generic delving mechanisms on top
   */
  def stageForIndexing(doc: SolrInputDocument)(implicit configuration: OrganizationConfiguration) {
    import scala.collection.JavaConversions._

    val hasDigitalObject: Boolean = !doc.entrySet().filter(entry => entry.getKey.startsWith(THUMBNAIL.tag) && !entry.getValue.isEmpty).isEmpty
    if (doc.containsKey(HAS_DIGITAL_OBJECT.key)) doc.remove(HAS_DIGITAL_OBJECT.key)
    doc.addField(HAS_DIGITAL_OBJECT.key, hasDigitalObject)

    if (hasDigitalObject) doc.setDocumentBoost(1.4.toFloat)

    val hasLandingPage: Boolean = !doc.entrySet().filter(entry => entry.getKey.startsWith(LANDING_PAGE.tag) && !entry.getValue.isEmpty).isEmpty
    if (doc.containsKey(HAS_LANDING_PAGE.key)) doc.remove(HAS_LANDING_PAGE.key)
    doc.addField(HAS_LANDING_PAGE.key, hasLandingPage)

    if (!doc.containsKey(VISIBILITY.key)) {
      doc.addField(VISIBILITY.key, Visibility.PUBLIC.value.toString)
    }

    // add full text from digital objects
    val fullTextUrl: Option[SolrInputField] =
      Option(doc.get("%s_link".format(FULL_TEXT_OBJECT_URL.key)))

    fullTextUrl foreach { url =>
      // we try to index this object - we don't know its type yet because the URL does not necessarily reflect the file name.
      log.debug(s"Indexing: Found a full text object for record: $url")
      val digitalObjectUrl = url.getFirstValue.toString
      Await.result(TikaIndexer.getFullTextFromRemoteURL(digitalObjectUrl), 10 seconds).map { text =>
        text.map(t => doc.addField("%s_text".format(FULL_TEXT.key), t))
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
    if (!doc.containsKey(RECORD_TYPE.key + "_facet")) {
      doc.addField(RECORD_TYPE.key + "_facet", doc.getField(RECORD_TYPE.key).getFirstValue)
    }
    if (!doc.containsKey(HAS_DIGITAL_OBJECT.key + "_facet")) {
      doc.addField(HAS_DIGITAL_OBJECT.key + "_facet", hasDigitalObject)
    }

    // clean geohash with type suffix
    if (doc.containsKey(GEOHASH.key + "_geohash")) {
      val doubleKey = GEOHASH.key + "_geohash"
      val field: SolrInputField = doc.get(doubleKey)
      doc.remove(doubleKey)
      field.getValues.toList.foreach(geoHash =>
        doc.addField(GEOHASH.key, geoHash)
      )
    }

    // *remove entries that are not valid lat,long pair
    // TODO this can probably be removed now that validation is being done upfront
    if (doc.containsKey(GEOHASH.key)) {
      val values = doc.get(GEOHASH.key).getValues.toList
      doc.remove(GEOHASH.key)
      val validCoordinates: List[String] = SOLRIndexingService.filterForValidGeoCoordinate(values)
      validCoordinates.foreach { geoHash =>
        doc.addField(GEOHASH.key, geoHash)
      }
      if (!validCoordinates.isEmpty) doc.addField(GEOHASH_MONO.key, validCoordinates.head)
    }

    doc.addField(HAS_GEO_HASH.key.toString, doc.containsKey(GEOHASH.key) && !doc.get(GEOHASH.key).isEmpty)

    getStreamingUpdateServer(configuration).add(doc)
  }

  /**
   * Commits staged Things or MDRs to index
   */
  def commit(implicit configuration: OrganizationConfiguration) {
    getStreamingUpdateServer(configuration).commit()
  }

  /**
   * Rolls back staged indexing requests
   */
  def rollback(implicit configuration: OrganizationConfiguration) {
    getStreamingUpdateServer(configuration).rollback()
  }

  /**
   * Deletes from the index by string ID
   */
  def deleteById(id: String)(implicit configuration: OrganizationConfiguration) {
    getStreamingUpdateServer(configuration).deleteById(id)
    commit
  }

  /**
   * Deletes from the index by query
   */
  def deleteByQuery(query: String)(implicit configuration: OrganizationConfiguration) {
    SolrServer.deleteFromSolrByQuery(query)
    commit
  }

  /**
   * Deletes from the index by collection spec
   */
  def deleteBySpec(orgId: String, spec: String)(implicit configuration: OrganizationConfiguration) {
    val deleteQuery = SPEC.tag + ":" + spec + " " + ORG_ID.key + ":" + orgId
    log.info("Deleting dataset from Solr Index: %s".format(deleteQuery))
    val deleteResponse = getStreamingUpdateServer(configuration).deleteByQuery(deleteQuery)
    deleteResponse.getStatus
    commit
  }

  def deleteOrphansBySpec(orgId: String, spec: String, startIndexing: DateTime)(implicit configuration: OrganizationConfiguration) {
    val fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    val deleteQuery = SPEC.tag + ":" + spec + " AND " + ORG_ID.key + ":" + orgId + " AND timestamp:[* TO " + fmt.print(startIndexing.minusSeconds(15)) + "]"
    val orphans = getSolrServer(configuration).query(new SolrQuery(deleteQuery)).getResults.getNumFound
    if (orphans > 0) {
      try {
        val deleteResponse = getStreamingUpdateServer(configuration).deleteByQuery(deleteQuery)
        deleteResponse.getStatus
        commit
        log.info("Deleting orphans %s from dataset from Solr Index: %s".format(orphans.toString, deleteQuery))
      } catch {
        case e: Exception => log.info("Unable to remove orphans for %s because of %s".format(spec, e.getMessage))
      }
    } else
      log.info("No orphans found for dataset in Solr Index: %s".format(deleteQuery))
  }

}

object SOLRIndexingService {

  /**
   * Check the values of an collection and remove all entries that are not valid lat long pairs
   * This check is not absolute but should remove most issues associated with wrong string values being assigned
   * in the mapping stage
   */
  def filterForValidGeoCoordinate(values: List[AnyRef]): List[String] = {
    def isValid(value: String) = {
      val coordinates = value.split(",")
      (coordinates.size == 2) && (coordinates.head.split("\\.").size == 2 && coordinates.last.split("\\.").size == 2)
    }
    values.map(_.toString.replaceAll(" ", "")).filter(isValid(_))
  }

}

object TikaIndexer extends HTTPClient {

  val log = Logger("CultureHub")

  def getFullTextFromRemoteURL(url: String): Future[Option[String]] = {
    try {
      log.info("Retrieving document for indexing with Tika at " + url)
      getObject(url).map(parseFullTextFromPdf)
    } catch {
      case t: Throwable =>
        log.error("Unable to process digital object found at " + url)
        Future(None)
    }
  }

  def getObject(url: String): Future[InputStream] = {
    WS.url(url).get().map(_.getAHCResponse.getResponseBodyAsStream)
  }

  def parseFullTextFromPdf(input: InputStream): Option[String] = {
    val textHandler = new BodyContentHandler()
    val metadata = new Metadata()
    val parser = new PDFParser()
    try {
      parser.parse(input, textHandler, metadata, new ParseContext)
      log.debug("Found following text via Tika for indexing:\n\n" + textHandler.toString)
      Some(textHandler.toString)
    } catch {
      case t: Throwable => None
    } finally {
      input.close()
    }
  }
}
