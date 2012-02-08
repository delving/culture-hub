package test

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._
import models._
import play.api.templates.Txt
import play.api.mvc.{AnyContentAsText, AnyContent, Content}

class SipCreatorEndPointSpec extends Specification {

  "SipCreatorEndPoint" should {

    "list all DataSets" in {

      running(FakeApplication()) {
        val result = controllers.SipCreatorEndPoint.listAll(Some("TEST"))(FakeRequest())
        status(result) must equalTo(OK)
        contentAsString(result) must contain("<spec>EmptyCollection</spec>")
      }

    }

    "unlock a DataSet" in {

      import com.mongodb.casbah.Imports._
      val bob = User.findByUsername("bob").get
      DataSet.update(MongoDBObject("spec" -> "EmptyCollection"), $set("lockedBy" -> bob._id))

      running(FakeApplication()) {
        val result = controllers.SipCreatorEndPoint.unlock("delving", "EmptyCollection", Some("TEST"))(FakeRequest())
        status(result) must equalTo(OK)
        DataSet.findBySpecAndOrgId("EmptyCollection", "delving").get.lockedBy must be(None)
      }

    }

    "accept a list of files" in {
      running(FakeApplication()) {

        val lines = """15E64004081B71EE5CA8D55EF735DE44__hints.txt
                       19EE613335AFBFFAD3F8BA271FBC4E96__mapping_icn.xml
                       45109F902FCE191BBBFC176287B9B2A4__source.xml.gz
                       19EE613335AFBFFAD3F8BA271FBC4E96__valid_icn.bit"""

        val result = controllers.SipCreatorEndPoint.acceptFileList("delving", "EmptyCollection", Some("TEST"))(
          FakeRequest(
            method = "POST",
            body = new AnyContentAsText(lines.stripMargin),
            uri = "",
            headers = FakeHeaders()
          ))
        status(result) must equalTo(OK)
        contentAsString(result) must equalTo (lines)
      }

    }


  }

}

