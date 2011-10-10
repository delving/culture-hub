package search

import play.test.UnitSpec
import org.scalatest.matchers.ShouldMatchers
import controllers.SolrServer

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 10/9/11 11:59 PM  
 */

class SolrSearchSpec extends UnitSpec with ShouldMatchers with SolrServer {

  describe("A SolrQuery") {

      describe("(when executed against an empty index)") {

        it("should return nothing") {
          import org.apache.solr.client.solrj.SolrQuery
          runQuery(new SolrQuery("sjoerd")).getResults.getNumFound should be (0)
        }

      }

    }

}