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

  describe("A SolrQueryHelper") {

      describe("(when getting SolrFields)") {

        it("should get all fields") {
           SolrServer.getSolrFields.size should not be (0)
        }

    }
    }

  private def createRequest(params : Map[String, Array[String]]) : Request = {
    val queryString = "bla" // todo replace with something better later
    val request = Request.createRequest("localhost", "GET", "/search", queryString, "text/xml", null, null, null, false,
    80, "delving.org", false, null, null)
    request
  }

  val lukeOutput =
    <response>
      <lst name="responseHeader">
        <int name="status">0</int>
        <int name="QTime">131</int>
      </lst>
      <lst name="index">
        <int name="numDocs">99</int>
        <int name="maxDoc">99</int>
        <int name="numTerms">9812</int>
        <long name="version">1318197555572</long>
        <bool name="optimized">true</bool>
        <bool name="current">true</bool>
        <bool name="hasDeletions">false</bool>
        <str name="directory">
          org.apache.lucene.store.NIOFSDirectory:org.apache.lucene.store.NIOFSDirectory@/Users/kiivihal/Experiments/gitHub/delving/culture-hub/extras/servlet-server/solr/core2/data/index lockFactory=org.apache.lucene.store.NativeFSLockFactory@5a10c276
        </str>
        <date name="lastModified">2011-11-08T06:01:11Z</date>
      </lst>
      <lst name="fields">
        <lst name="europeana_uri">
          <str name="type">string</str>
          <str name="schema">I-SM---OF----l</str>
          <str name="index">I-S----O----</str>
          <int name="docs">99</int>
          <int name="distinct">99</int>
          <lst name="topTerms">
            <int name="node/101">1</int>
            <int name="node/102">1</int>
            <int name="node/104">1</int>
            <int name="node/105">1</int>
            <int name="node/106">1</int>
            <int name="node/108">1</int>
            <int name="node/109">1</int>
            <int name="node/110">1</int>
            <int name="node/111">1</int>
            <int name="node/114">1</int>
          </lst>
          <lst name="histogram">
            <int name="1">99</int>
          </lst>
        </lst>
      </lst>
    </response>

}
