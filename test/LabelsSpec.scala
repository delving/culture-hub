import java.util.Date
import models.{DObject, Label}
import org.scalatest.matchers.ShouldMatchers
import play.mvc.Http.{Request, Response}
import play.mvc.Scope.Session
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

  it should "create a freeText label" in {

    val req = getAuthenticated()
    Map("labelType" -> "freeText", "value" -> "toto").foreach {p => req.params.put(p._1, p._2)}

    val res: Response = FunctionalTest.POST(req, "/bob/label")
    res.status should be (200)
    res.contentType should be ("application/json; charset=utf-8")

    Label.findOne(MongoDBObject("labelType" -> "freeText", "value" -> "toto")) should not be (None)
  }

  it should "not create just any label" in {
    val req = getAuthenticated()
    Map("labelType" -> "tatata", "value" -> "toto").foreach {p => req.params.put(p._1, p._2)}

    val res: Response = FunctionalTest.POST(req, "/bob/label")
    res.status should be (500)

  }

  it should "add a label to an object" in {

    val l = Label.create("freeText", "bob", "toto")
    val label = Label.findOne(MongoDBObject("_id" -> l.get)).get

    val dobject = DObject.findOne(MongoDBObject()).get

    val req = getAuthenticated()
    req.querystring = "?targetType=DObject"

    FunctionalTest.PUT(req, "/bob/object/%s/label/%s".format(dobject._id.toString, label._id.toString), "text/plain", "")

    val updatedObject = DObject.findOneByID(dobject._id).get

    updatedObject.labels.size should be (1)
    val embedded = updatedObject.labels.head
    embedded.label should equal (label._id)
    embedded.userName should equal ("bob")

    val updatedLabel = Label.findOneByID(l.get).get
    updatedLabel.references.size should be (1)
    updatedLabel.references.head.id should be (dobject._id)
    updatedLabel.references.head.targetType should be ("DObject")

  }

  def getAuthenticated() = {
    val login = FunctionalTest.POST("/login", Map("username" -> "bob", "password" -> "secret"))
    val req = FunctionalTest.newRequest()
    req.cookies = login.cookies
    req
  }

}