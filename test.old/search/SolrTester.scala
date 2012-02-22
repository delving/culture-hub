package search

import org.apache.solr.common.SolrInputDocument
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.client.solrj. {SolrQuery, SolrServer}
import java.lang.System

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since May 10, 2010 9:22:37 AM
 */

trait SolrTester extends SolrServer {

//  val dir = System.getProperty("user.dir");
//  println("CURRENT WORKING DIRECTORY IS " + dir)
//
//  def loadDefaultData(server: SolrServer, numberOfRecords: Int = 14, collId: String = "92001"): Unit = {
//    server.deleteByQuery("*:*") // delete all records in the index
//    val docs = for (identifier <- 1 to numberOfRecords) yield createSolrRecord(identifier.toString, collId)
//    server.add(docs)
//    server.commit // commit so that data becomes available immediately
//  }
//
//  def createEuropeanaUri(identifier: String, collId: String) : String = "%s/%s".format(collId, identifier)
//
//  private def createSolrRecord(identifier: String, collId: String) : SolrInputDocument = {
//    val doc = new SolrInputDocument
//    doc.addField("europeana_uri", createEuropeanaUri(identifier, collId))
//    doc.addField("europeana_type", "TEXT")
//    List("europeana_collectionName", "europeana_collectionTitle", "europeana_provider", "europeana_dataProvider").foreach(field =>
//      doc.addField(field, format("%s=%s", field, identifier))
//    )
//    List("dc_title", "dc_creator", "dc_date").foreach{field =>
//      doc.addField(field, format("%s=%s", field, identifier))
//      doc.addField(field, format("%s=%s", field, identifier + "2"))
//    }
//    doc
//  }
//
//  def getQueryResponse(query: String = "*:*", start: Int = 0) : QueryResponse = {
//    loadDefaultData(solrServer)
//    solrServer.query(new SolrQuery(query).setStart(start))
//  }

}