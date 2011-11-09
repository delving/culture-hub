package controllers

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/7/11 1:18 AM  
 */

trait SolrServer {

  import org.apache.solr.client.solrj.SolrQuery
  import org.apache.solr.client.solrj.response.QueryResponse

  // todo: later move to cake pattern type trait and instantiation

  def getSolrServer = SolrServer.solrServer
  def getStreamingUpdateServer = SolrServer.streamingUpdateServer
  def getTestServer = SolrServer.solrTestServer

  def runQuery(query: SolrQuery, solrBaseUrl: String): QueryResponse = SolrServer.getSolrServer(solrBaseUrl).query(query)
}

object SolrServer {
  import org.apache.solr.client.solrj.impl.{StreamingUpdateSolrServer, CommonsHttpSolrServer}

  def getSolrServer(url: String) = {
    solrServer.setBaseURL(url)
    solrServer
  }

  def getSolrUpdateServer(url: String) = {
    streamingUpdateServer.setBaseURL(url)
    streamingUpdateServer
  }

  private val url = "http://localhost:8983/solr/core2"
  private val solrServer = new CommonsHttpSolrServer( url )
  solrServer.setSoTimeout(10000)  // socket read timeout
  solrServer.setConnectionTimeout(100)
  solrServer.setDefaultMaxConnectionsPerHost(100)
  solrServer.setMaxTotalConnections(100)
  solrServer.setFollowRedirects(false)  // defaults to false
  // allowCompression defaults to false.
  // Server side must support gzip or deflate for this to have any effect.
  solrServer.setAllowCompression(false)
  solrServer.setMaxRetries(0) // defaults to 0.  > 1 not recommended.

  private val streamingUpdateServer = new StreamingUpdateSolrServer(url, 5000, 30)
  streamingUpdateServer.setSoTimeout(10000)  // socket read timeout
  streamingUpdateServer.setConnectionTimeout(100)
  streamingUpdateServer.setDefaultMaxConnectionsPerHost(100)
  streamingUpdateServer.setMaxTotalConnections(100)
  streamingUpdateServer.setFollowRedirects(false)  // defaults to false
  streamingUpdateServer.setAllowCompression(false)
  streamingUpdateServer.setMaxRetries(0) // defaults to 0.  > 1 not recommended.

  
  private val testUrl = "http://localhost:8983/solr/core3"
  private val solrTestServer = new CommonsHttpSolrServer( testUrl )
  solrTestServer.setSoTimeout(10000)  // socket read timeout
  solrTestServer.setConnectionTimeout(100)
  solrTestServer.setDefaultMaxConnectionsPerHost(100)
  solrTestServer.setMaxTotalConnections(100)
  solrTestServer.setFollowRedirects(false)  // defaults to false
  
//  def getSolrFields(solrUrl: String) {
//    import xml.XML
//    import java.net.URL
//    val lukeUrl: URL = new URL("%s/admin/luke".format(solrUrl))
//    val fields = XML.load(lukeUrl) \\ "lst" \\ "lst"
//    fields.map {
//      field => {
//        val fieldName = field.attribute("name").get.text
//        field.nonEmptyChildren match {
//          case n @ <str>{text}</str> if (n \ "@name")  == "type" =>
//        }
//      }
//    }
//  }
  
}

case class SolrDynamicField(name: String,  fieldType: String,  docs: Int, distinct: Int, topTerms: List[SolrFrequencyItem] = List.empty, histogram: List[SolrFrequencyItem] = List.empty)
case class SolrFrequencyItem(name: String,  freq: Int)