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

  describe("A SolrQueryHelper") {

    describe("(when getting SolrFields)") {

      it("should get all fields")(pending)
    }
  }
}
