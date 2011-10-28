package controllers.search

import org.apache.solr.client.solrj.SolrQuery
import play.mvc.Scope.Params
import models.PortalTheme
import org.apache.solr.client.solrj.response.FacetField

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/28/11 10:52 AM  
 */

object SolrQueryService {

    import org.apache.log4j.Logger
  import xml.Elem
  import play.mvc.Http.Request
  import java.net.URLEncoder

  private val log : Logger = Logger.getLogger("RichSearchAPIService")

  def renderXMLFields(field : FieldValue, response: CHResponse) : Seq[Elem] = {
    field.getValueAsArray.map(value =>
      try {
        import xml.XML
        XML.loadString("<%s>%s</%s>\n".format(field.getKeyAsXml, encodeUrl(value, field, response), field.getKeyAsXml))
      }
      catch {
        case ex : Exception =>
          log error ("unable to parse " + value + "for field " + field.getKeyAsXml)
          <error/>
      }
    ).toSeq
  }

  def encodeUrl(field: FieldValue, request: Request, response: CHResponse): String = {
    import java.net.URLEncoder
    if (response.useCacheUrl && field.getKey == "europeana_object")
      response.theme.cacheUrl + URLEncoder.encode(field.getFirst, "utf-8")
    else field.getFirst
  }

  def encodeUrl(fields: Array[String], label: String, response: CHResponse): Array[String] = {

    if (response.useCacheUrl && label == "europeana_object")
      fields.map(fieldEntry => response.theme.cacheUrl + URLEncoder.encode(fieldEntry, "utf-8"))
    else fields
  }

  def encodeUrl(content: String, field: FieldValue, response: CHResponse): String = {
    import java.net.URLEncoder
    if (response.useCacheUrl && field.getKey == "europeana_object")
      response.theme.cacheUrl + URLEncoder.encode(content, "utf-8")
    else if (content.startsWith("http://")) content.replaceAll("&", "&amp;")
    else content.replaceAll(" & ", "&amp;")
  }

   import org.apache.solr.client.solrj.SolrQuery
  import play.mvc.Http.Request
  import models.{PortalTheme, FacetElement}

  def getSolrQueryWithDefaults(facets: List[FacetElement] = List.empty): SolrQuery = {

    val query = new SolrQuery("*:*")
    query set ("edismax")
    query setRows 12
    query setStart 0
    // todo get facets from themes
    query setFacet true
    query setFacetLimit (1)
    query setFacetLimit (100)
    facets foreach (facet => query setFacetPrefix (facet.facetPrefix, facet.facetName))

    query
  }


  def parseRequest(request: Request, theme: PortalTheme) : SolrQuery = {
    import scala.collection.JavaConversions._

    val query = getSolrQueryWithDefaults(theme.facets)

    val params = request.params
//    require(params._contains("query") && !params.get("query").isEmpty)



    params.all().foreach{
      key =>
        val values = key._2
        key._1 match {
          case "query" =>
            query setQuery (values.head)
          case "start" =>
            if (!values.isEmpty) query setStart (values.head.toInt)
          case "rows" =>
            if (!values.isEmpty) query setRows (values.head.toInt)
          case "format" =>
//            if (!values.isEmpty) query ssetRows (values.head.toInt)
          case "qf" =>

          case "hqf" =>
          case "id" =>
          case "explain" =>
          case _ =>
        }
    }
    query
  }

  def createRandomNumber: Int = scala.util.Random.nextInt(1000)

  def createRandomSortKey : String = "random_%i".format(createRandomNumber)
}
case class FilterQuery(field: String, value: String)

case class CHQuery(solrQuery: SolrQuery, responseFormat: String, filterQueries: List[FilterQuery] = List.empty, hiddenFilterQueries: List[FilterQuery] = List.empty) {

}

case class CHResponse(breadCrumbs: List[BreadCrumb], params: Params, theme: PortalTheme) { // todo extend with the other response elements

  def useCacheUrl: Boolean = if (!params._contains("cache") && !params.get("cache").equalsIgnoreCase("true")) true else false

}

/*
 * case classes converted from legacy code
 */

case class PageLink(start: Int, display: Int, isLinked: Boolean = false) {
  override def toString: String = if (isLinked) "%i:%i".format(display, start) else display.toString
}

case class BreadCrumb(href: String, display: String, field: String, localisedField: String, value: String, isLast: Boolean) {
  override def toString: String = "<a href=\"" + href + "\">" + display + "</a>"
}

case class FacetCountLink(facetCount: FacetField.Count, url: String, remove: Boolean) {

  def value = facetCount.getName
  def count = facetCount.getCount

  override def toString: String = "<a href='%s'>%s</a> (%s)".format(url, value, if (remove) "remove" else "add")
}

case class FacetQueryLinks(facetType: String, links: List[FacetCountLink] = List.empty, facetSelected: Boolean = false)

class MalformedQueryException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this (s, null)
}