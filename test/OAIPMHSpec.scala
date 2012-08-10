import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._

/**
 * TODO actually test the things we get back
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class OAIPMHSpec extends TestContext {

  step {
    loadStandalone()
  }

  "the OAI-PMH repository" should {

    "return a valid identify response" in {

      withTestConfig {

        val request = FakeRequest("GET", "?verb=Identify")
        val r = controllers.api.OaiPmh.oaipmh("delving", None, None)(request)

        val response = asyncToResult(r)

        status(response) must equalTo(OK)

        val xml = contentAsXML(response)

        val error = xml \ "error"
        error.length must equalTo(0)
      }

    }

    "list sets" in {

      withTestConfig {

        val request = FakeRequest("GET", "?verb=ListSets")
        val r = controllers.api.OaiPmh.oaipmh("delving", None, None)(request)

        val response = asyncToResult(r)

        status(response) must equalTo(OK)


        val xml = contentAsXML(response)
        val error = xml \ "error"
        if (error.length != 0) {
          println(error)
        }
        error.length must equalTo(0)

        val sets = xml \ "ListSets" \ "set"
        sets.size must equalTo(1)
        (sets \ "setSpec").text must equalTo("PrincessehofSample")
      }
    }

    "list formats" in {

      withTestConfig {
        val request = FakeRequest("GET", "?verb=ListMetadataFormats")
        val r = controllers.api.OaiPmh.oaipmh("delving", Some("icn"), None)(request)

        val response = asyncToResult(r)

        status(response) must equalTo(OK)

        (contentAsXML(response) \\ "metadataFormat").size must equalTo (1)
        ((contentAsXML(response) \\ "metadataFormat").head \ "metadataPrefix").text must equalTo("icn")

      }

    }

    "list records" in {

      withTestConfig {

        val request = FakeRequest("GET", "?verb=ListRecords&set=PrincessehofSample&metadataPrefix=icn")
        val r = controllers.api.OaiPmh.oaipmh("delving", None, None)(request)

        val response = asyncToResult(r)

        status(response) must equalTo(OK)

        val xml = contentAsXML(response)
        val error = xml \ "error"
        error.length must equalTo(0)

        val records = xml \ "ListRecords" \ "record"
        records.length must equalTo(6) // 6 valid records for ICN
      }

    }

  }

  step(cleanup())


}
