import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._
import models._
import play.api.mvc._
import java.io.{File, FileInputStream}
import play.api.libs.Files.TemporaryFile
import io.Source
import play.api.libs.Files

class SipCreatorEndPointSpec extends Specification {

  "SipCreatorEndPoint" should {

    "list all DataSets" in {

      running(FakeApplication()) {
        val result = controllers.SipCreatorEndPoint.listAll(Some("TEST"))(FakeRequest())
        status(result) must equalTo(OK)
        contentAsString(result) must contain("<spec>PrincessehofSample</spec>")
      }

    }

    "unlock a DataSet" in {

      import com.mongodb.casbah.Imports._
      val bob = User.findByUsername("bob").get
      DataSet.update(MongoDBObject("spec" -> "PrincessehofSample"), $set("lockedBy" -> bob._id))

      running(FakeApplication()) {
        val result = controllers.SipCreatorEndPoint.unlock("delving", "PrincessehofSample", Some("TEST"))(FakeRequest())
        status(result) must equalTo(OK)
        DataSet.findBySpecAndOrgId("PrincessehofSample", "delving").get.lockedBy must be(None)
      }

    }

    "accept a list of files" in {
      running(FakeApplication()) {

        val lines = """15E64004081B71EE5CA8D55EF735DE44__hints.txt
                       19EE613335AFBFFAD3F8BA271FBC4E96__mapping_icn.xml
                       45109F902FCE191BBBFC176287B9B2A4__source.xml.gz
                       19EE613335AFBFFAD3F8BA271FBC4E96__valid_icn.bit"""

        val result = controllers.SipCreatorEndPoint.acceptFileList("delving", "PrincessehofSample", Some("TEST"))(
          FakeRequest(
            method = "POST",
            body = new AnyContentAsText(lines.stripMargin),
            uri = "",
            headers = FakeHeaders()
          ))
        status(result) must equalTo(OK)
        contentAsString(result) must equalTo(lines)
      }

      // TODO when we have a test DataSet, check this against the existing hashes
    }

    "accept a hints file" in {
      running(FakeApplication()) {
        val hintsSource: String = "conf/bootstrap/E6D086CAC8F6316F70050BC577EB3920__hints.txt"
        val hintsTarget = "target/E6D086CAC8F6316F70050BC577EB3920__hints.txt"
        Files.copyFile(new File(hintsSource), new File(hintsTarget))

        val result = controllers.SipCreatorEndPoint.acceptFile("delving", "PrincessehofSample", "E6D086CAC8F6316F70050BC577EB3920__hints.txt", Some("TEST"))(FakeRequest(
          method = "POST",
          uri = "",
          headers = FakeHeaders(),
          body = TemporaryFile(new File(hintsTarget))
        ))
        status(result) must equalTo(OK)
        val stream = new FileInputStream(new File(hintsSource))
        val data = Stream.continually(stream.read).takeWhile(-1 !=).map(_.toByte).toArray
        DataSet.findBySpecAndOrgId("PrincessehofSample", "delving").get.hints must equalTo(data)
      }
    }


    "accept a mappings file" in {
      running(FakeApplication()) {
        val mappingSource: String = "conf/bootstrap/D15C73DFD3463F1D0281232BA54301C1__mapping_icn.xml"
        val mappingTarget = "target/D15C73DFD3463F1D0281232BA54301C1__mapping_icn.xml"
        Files.copyFile(new File(mappingSource), new File(mappingTarget))

        val result = controllers.SipCreatorEndPoint.acceptFile("delving", "PrincessehofSample", "D15C73DFD3463F1D0281232BA54301C1__mapping_icn.xml", Some("TEST"))(FakeRequest(
          method = "POST",
          uri = "",
          headers = FakeHeaders(),
          body = TemporaryFile(new File(mappingTarget))
        ))
        status(result) must equalTo(OK)
        val original = Source.fromFile(new File(mappingSource)).getLines().mkString("\n")
        val uploaded = DataSet.findBySpecAndOrgId("PrincessehofSample", "delving").get.mappings("icn").recordMapping.get

        // TODO fix this
        original.hashCode() must equalTo(uploaded.hashCode())

      }
    }
  }


}

