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

  it should "add a label to an object" in {

    val dobject = DObject.findOne(MongoDBObject()).get

    val req = getAuthenticated()
    req.method = "POST"
    FunctionalTest.POST(req, "/bob/object/%s/label".format(dobject._id.toString), Map("label" -> "toto"), Map.empty[String, File])

    val updatedObject = DObject.findOneByID(dobject._id).get
    val link = Link.findOne(MongoDBObject("value.label" -> "toto"))

    link should not be (None)

    updatedObject.labels.size should be (1)
    val embedded = updatedObject.labels.head
    embedded.link should equal (link.get._id)
    embedded.userName should equal ("bob")
    embedded.value.label should be ("toto")
  }

  it should "remove a label from an object" in {
    val dobject = DObject.findOne(MongoDBObject()).get
    val link = Link.findOne(MongoDBObject("value.label" -> "toto")).get

    val req = getAuthenticated()
    req.method = "DELETE"
    val response: Response = FunctionalTest.DELETE(req, "/bob/object/%s/label/%s".format(dobject._id.toString, link._id.toString))

    response.status should be (200)

    Link.count(MongoDBObject("value.label" -> "toto")) should equal (0)

    val updatedObject = DObject.findOneByID(dobject._id).get
    updatedObject.labels.size should equal (0)
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