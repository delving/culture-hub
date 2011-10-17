package search

import play.test.UnitSpec
import org.scalatest.matchers.ShouldMatchers
import controllers.SolrServer
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.FacetField
import org.apache.solr.client.solrj.response.FacetField.Count

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/9/11 11:59 PM  
 */

class SolrSearchSpec extends UnitSpec with ShouldMatchers with SolrServer {

  import play.mvc.Http.Request

  describe("A SolrQuery") {

      describe("(when executed against an empty index)") {

        it("should return nothing") {
          import org.apache.solr.client.solrj.SolrQuery
          runQuery(new SolrQuery("sjoerd")).getResults.getNumFound should be (0)
        }

      }
    }

  describe("A SolrQueryHelper") {



      describe("(when )") {

        it("should ")(pending)

    }
    }

  private def createRequest(params : Map[String, Array[String]]) : Request = {
    val queryString = "bla" // todo replace with something better later
    val request = Request.createRequest("localhost", "GET", "/search", queryString, "text/xml", null, null, null, false,
    80, "delving.org", false, null, null)
    request
  }
}
case class FilterQuery(field: String, value: String)

case class CHQuery(solrQuery: SolrQuery, responseFormat: String, filterQueries: List[FilterQuery] = List.empty, hiddenFilterQueries: List[FilterQuery] = List.empty)

case class CHResponse(breadCrumbs: List[BreadCrumb])// todo extend with the other response elements

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

object SolrQueryHelper {

  import org.apache.solr.client.solrj.SolrQuery
  import play.mvc.Http.Request
  import models.{PortalTheme, FacetElement}

  def getSolrQueryWithDefaults(facets: List[FacetElement] = List.empty): SolrQuery = {

    val query = new SolrQuery()
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