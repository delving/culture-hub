import org.specs2.mutable.Specification
import play.api.test.Helpers._
import play.api.test.FakeRequest

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

class SearchApiSpec extends Specification with TestContext {

  step {
    withTestConfig {
      load()
      loadDataSet()
    }
  }

  "the Search API" should {

    "find all records" in {

      withTestConfig {

        val response = query("*:*&delving_spec:PrincessehofSample")
        status(response) must equalTo(OK)
        val results = contentAsXML(response)

        val numFound = (results \ "query" \ "@numFound").text.toInt
        numFound must equalTo(7)

      }
    }
  }

  "find one record by hubId" in {

    withTestConfig {


      val response = id("delving_PrincessehofSample_8")
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

}