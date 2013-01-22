import play.api.test.Helpers._
import play.api.test.FakeRequest
import util.OrganizationConfigurationHandler

/**
 * TODO better check of the content of all records & search by ID
 * TODO hubIds with spaces in them
 * TODO hubIds with weird characters (utf-8)
 * TODO hubIds with URL-encoded characters
 * TODO search by legacy ID
 * TODO etc.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class SearchApiSpec extends Specs2TestContext {

  args(skipAll=true)

  step {
    loadStandalone(SAMPLE_A, SAMPLE_B)
  }

  "the Search API" should {

    "find all records" in {

      withTestConfig {

        implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")

        val response = query("delving_spec:sample-b")
        status(response) must equalTo(OK)
        val results = contentAsXML(response)

        val numFound = (results \ "query" \ "@numFound").text.toInt

        numFound must equalTo(299)
      }
    }
  }

  "find one record by hubId" in {

    withTestConfig {

      val response = id("delving_sample-b_oai-jhm-50000019")
      status(response) must equalTo(OK)
      val result = contentAsXML(response)

      (result \ "item").length must equalTo(1)

    }
  }

  private def query(query: String) = {
    val request = FakeRequest("GET", "?query=" + query)
    val r = controllers.api.Search.searchApi("delving", None, None, None)(request)
    asyncToResult(r)
  }

  private def id(id: String) = {
    val request = FakeRequest("GET", "?id=" + id)
    val r = controllers.api.Search.searchApi("delving", None, None, None)(request)
    asyncToResult(r)
  }

  step {
    cleanup()
  }

}