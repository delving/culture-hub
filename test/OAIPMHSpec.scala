import controllers.SipCreatorEndPoint
import java.io.{File, FileInputStream}
import java.util.zip.GZIPInputStream
import models.DataSet
import org.specs2.mutable._
import play.api.mvc.{Result, AsyncResult}
import play.api.test._
import play.api.test.Helpers._
import xml.XML

/**
 * TODO actually test the things we get back
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class OAIPMHSpec extends Specification with TestData {

  def asyncToResult(response: Result) = response.asInstanceOf[AsyncResult].result.await.get

  def contentAsXML(response: Result) = XML.loadString(contentAsString(response))

  step {
    running(FakeApplication()) {
      load
      val dataSet = DataSet.findBySpecAndOrgId("PrincessehofSample", "delving").get
      SipCreatorEndPoint.loadSourceData(dataSet, new GZIPInputStream(new FileInputStream(new File("conf/bootstrap/EA525DF3C26F760A1D744B7A63C67247__source.xml.gz"))))
    }
  }

  "the OAI-PMH repository" should {

    "return a valid identify response" in {

      running(FakeApplication()) {

        val request = FakeRequest("GET", "?verb=Identify")
        val r = controllers.Services.oaipmh("delving", None)(request)

        val response = asyncToResult(r)

        status(response) must equalTo(OK)

        val xml = contentAsXML(response)

        val error = xml \ "error"
        error.length must equalTo(0)


      }

    }

    "list sets" in {

      running(FakeApplication()) {

        val request = FakeRequest("GET", "?verb=ListSets")
        val r = controllers.Services.oaipmh("delving", None)(request)

        val response = asyncToResult(r)

        status(response) must equalTo(OK)


        val xml = contentAsXML(response)
        val error = xml \ "error"
        error.length must equalTo(0)

        val sets = xml \ "ListSets" \ "set"
        sets.size must equalTo(1)
        (sets \ "setSpec").text must equalTo("PrincessehofSample")
      }
    }

    "list records" in {

      running(FakeApplication()) {

        val request = FakeRequest("GET", "?verb=ListRecords&set=PrincessehofSample&metadataPrefix=icn")
        val r = controllers.Services.oaipmh("delving", None)(request)

        val response = asyncToResult(r)

        status(response) must equalTo(OK)

          // TODO this doesn't work in test mode yet since there are no cached values of the transformed records available

//        val xml = contentAsXML(response)
//        val error = xml \ "error"
//        error.length must equalTo(0)

      }

    }

  }

  step(cleanup)


}
