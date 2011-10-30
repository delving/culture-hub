package search

import play.test.UnitSpec
import org.scalatest.matchers.ShouldMatchers
import controllers.SolrServer
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.FacetField
import org.apache.solr.client.solrj.response.FacetField.Count
import play.mvc.Scope.Params
import models.PortalTheme

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
          runQuery(new SolrQuery("sjoerd"), "http://localhost:8983/solr/core2").getResults.getNumFound should be (0)
        }

        it("should return some results") {
          import org.apache.solr.client.solrj.SolrQuery
          runQuery(new SolrQuery("*:*"), "http://vps16.delving.org:8080/solr/").getResults.getNumFound should not be (0)
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
