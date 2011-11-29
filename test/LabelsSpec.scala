import java.io.File
import models.{Link, DObject}
import org.scalatest.matchers.ShouldMatchers
import play.mvc.Http.Response
import play.test.{FunctionalTest, UnitFlatSpec}
import scala.Predef._
import util.TestDataGeneric
import scala.collection.JavaConversions._
import com.mongodb.casbah.Imports._
/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class LabelsSpec extends UnitFlatSpec with ShouldMatchers with TestDataGeneric {

  it should "add a freetext link to an object" in {

    val dobject = DObject.findOne(MongoDBObject()).get

    val req = getAuthenticated()
    req.method = "POST"
    val response: Response = FunctionalTest.POST(req, "/bob/object/%s/link".format(dobject._id.toString), Map("label" -> "toto", "linkType" -> "freeText"), Map.empty[String, File])

    response.status should be (200)

    val updatedObject = DObject.findOneByID(dobject._id).get
    val link = Link.findOne(MongoDBObject("value.label" -> "toto"))

    link should not be (None)
    link.get.linkType should be (Link.LinkType.FREETEXT)

    updatedObject.links.size should be (1)
    val embedded = updatedObject.links.head
    embedded.link should equal (link.get._id)
    embedded.linkType should equal (Link.LinkType.FREETEXT)
    embedded.userName should equal ("bob")
    embedded.value("label") should be ("toto")
  }

  it should "remove a label from an object" in {
    val dobject = DObject.findOne(MongoDBObject()).get
    val link = Link.findOne(MongoDBObject("value.label" -> "toto")).get

    val req = getAuthenticated()
    req.method = "DELETE"
    val response: Response = FunctionalTest.DELETE(req, "/bob/object/%s/link/%s".format(dobject._id.toString, link._id.toString))

    response.status should be (200)

    Link.count(MongoDBObject("value.label" -> "toto")) should equal (0)

    val updatedObject = DObject.findOneByID(dobject._id).get
    updatedObject.links.size should equal (0)
  }

  it should "add a place link to an object and store some additional information" in {
    val dobject = DObject.findOne(MongoDBObject()).get

    val req = getAuthenticated()
    req.method = "POST"
    val response: Response = FunctionalTest.POST(req, "/bob/object/%s/link".format(dobject._id.toString), Map("label" -> "Earth", "linkType" -> "place", "geonameId" -> "42"), Map.empty[String, File])

    response.status should be (200)
    Link.count(MongoDBObject("value.label" -> "Earth", "value.geonameId" -> "42", "linkType" -> Link.LinkType.PLACE)) should equal (1)

    val embedded = DObject.findOneByID(dobject._id).get.links.head
    embedded.userName should equal ("bob")
    embedded.linkType should equal (Link.LinkType.PLACE)
    embedded.value("label") should be ("Earth")
    embedded.value("geonameId") should be ("42")

  }

  /*
  it should "fail if an unexisting label tries to be removed" in {
    val dobject = DObject.findOne(MongoDBObject()).get

    val req = getAuthenticated()
    req.method = "DELETE"
    val response: Response = FunctionalTest.DELETE(req, "/bob/object/%s/label/%s".format(dobject._id.toString, "somethingSomething"))

    response.status should be (500)
  }
  */

  def getAuthenticated() = {
    val login = FunctionalTest.POST("/login", Map("username" -> "bob", "password" -> "secret"))
    val req = FunctionalTest.newRequest()
    req.cookies = login.cookies
    req
  }

}