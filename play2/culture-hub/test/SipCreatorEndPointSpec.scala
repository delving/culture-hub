package test

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._
import models._

class SipCreatorEndPointSpec extends Specification {

  "SipCreatorEndPoint" should {

    "list all DataSets" in {

      running(FakeApplication()) {
        val result = controllers.SipCreatorEndPoint.listAll(Some("TEST"))(FakeRequest())
        contentAsString(result) must contain ("<spec>EmptyCollection</spec>")
        status(result) must equalTo(OK)
      }

    }
  }

}

