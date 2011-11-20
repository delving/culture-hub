package controllers

import org.bson.types.ObjectId
import org.apache.solr.client.solrj.response.UpdateResponse

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/7/11 1:18 AM  
 */

trait SolrServer {

  import org.apache.solr.client.solrj.SolrQuery
  import org.apache.solr.client.solrj.response.QueryResponse

  def getSolrServer = SolrServer.solrServer

  def getStreamingUpdateServer = SolrServer.streamingUpdateServer

  def runQuery(query: SolrQuery): QueryResponse = SolrServer.solrServer.query(query)

}

object SolrServer {

  import org.apache.solr.client.solrj.impl.{StreamingUpdateSolrServer, CommonsHttpSolrServer}
  import play.Play
  import xml.Node
  import org.apache.solr.common.SolrInputDocument

  private val url = Play.configuration.getProperty("solr.baseUrl", "http://localhost:8983/solr/core2")
  private val solrServer = new CommonsHttpSolrServer(url)
  solrServer.setSoTimeout(10000) // socket read timeout
  solrServer.setConnectionTimeout(100)
  solrServer.setDefaultMaxConnectionsPerHost(100)
  solrServer.setMaxTotalConnections(100)
  solrServer.setFollowRedirects(false) // defaults to false
  // allowCompression defaults to false.
  // Server side must support gzip or deflate for this to have any effect.
  solrServer.setAllowCompression(false)
  solrServer.setMaxRetries(0)
  // defaults to 0.  > 1 not recommended.

  private val streamingUpdateServer = new StreamingUpdateSolrServer(url, 2500, 5)
  streamingUpdateServer.setSoTimeout(10000) // socket read timeout
  streamingUpdateServer.setConnectionTimeout(100)
  streamingUpdateServer.setDefaultMaxConnectionsPerHost(100)
  streamingUpdateServer.setMaxTotalConnections(100)
  streamingUpdateServer.setFollowRedirects(false) // defaults to false
  streamingUpdateServer.setAllowCompression(false)
  streamingUpdateServer.setMaxRetries(0) // defaults to 0.  > 1 not recommended.

  def deleteFromSolrById(id: String): UpdateResponse = streamingUpdateServer.deleteById(id)
  def deleteFromSolrById(id: ObjectId): UpdateResponse = deleteFromSolrById(id.toString)

  def indexSolrDocument(doc: SolrInputDocument) = streamingUpdateServer.add(doc)

  def getSolrFrequencyItemList(node: Node): List[SolrFrequencyItem] = {
    node.nonEmptyChildren.filter(node => node.attribute("name") != None).map {
      f => SolrFrequencyItem(f.attribute("name").get.text, f.text.toInt)
    }.toList
  }

  def getSolrFields: List[SolrDynamicField] = {
    import xml.XML
    import java.net.URL
    val lukeUrl: URL = new URL("%s/admin/luke".format(url))
    val fields = XML.load(lukeUrl) \\ "lst"

    fields.filter(node => node.attribute("name") != None && node.attribute("name").get.text.equalsIgnoreCase("fields")).head.nonEmptyChildren.map {
      field => {
        val fieldName = field.attribute("name").get.text
        val fields = field.nonEmptyChildren
        fields.foldLeft(SolrDynamicField(name = fieldName))((sds, f) => {
          val text = f.attribute("name")
          text match {
            case Some(fieldtype) if (fieldtype.text == ("type")) => sds.copy(fieldType = f.text)
            case Some(fieldtype) if (fieldtype.text == ("index")) => sds.copy(index = f.text)
            case Some(fieldtype) if (fieldtype.text == ("schema")) => sds.copy(schema = f.text)
            case Some(fieldtype) if (fieldtype.text == ("dynamicBase")) => sds.copy(dynamicBase = f.text)
            case Some(fieldtype) if (fieldtype.text == "docs") => sds.copy(docs = f.text.toInt)
            case Some(fieldtype) if (fieldtype.text == "distinct") => sds.copy(distinct = f.text.toInt)
            case Some(fieldtype) if (fieldtype.text == "topTerms") => sds.copy(topTerms = getSolrFrequencyItemList(f))
            case Some(fieldtype) if (fieldtype.text == "histogram") => sds.copy(histogram = getSolrFrequencyItemList(f))
            case _ => sds
          }
        }
        )
      }
    }.toList
  }
}

case class SolrDynamicField(name: String, fieldType: String = "text", schema: String = "", index: String = "", dynamicBase: String = "", docs: Int = 0, distinct: Int = 0, topTerms: List[SolrFrequencyItem] = List.empty, histogram: List[SolrFrequencyItem] = List.empty) {

  import search.SolrBindingService

  lazy val fieldCanBeUsedAsFacet: Boolean = name.endsWith("_facet") || fieldType.equalsIgnoreCase("string") || name.startsWith("facet_")
  lazy val fieldIsSortable: Boolean = name.startsWith("sort_")

  lazy val xmlFieldName = normalisedField.replaceFirst("_", ":")
  lazy val normalisedField = SolrBindingService.stripDynamicFieldLabels(name)
}

case class SolrFrequencyItem(name: String, freq: Int)

