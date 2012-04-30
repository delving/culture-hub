import collection.immutable.List
import controllers.{ListItem, Search}
import controllers.user.DObjects
import core.Constants
import java.io.File
import models.{UserCollection, DataSet, Link, DObject}
import org.scalatest.matchers.ShouldMatchers
import play.mvc.Http.Response
import play.mvc.Scope
import play.test.{FunctionalTest, UnitFlatSpec}
import scala.Predef._
import scala.collection.JavaConversions._
import com.mongodb.casbah.Imports._
import util.Constants._
import util.{ThemeHandler, TestDataGeneric}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class LinksSpec extends UnitFlatSpec with ShouldMatchers with TestDataGeneric {

  it should "add a freetext link to an object" in {

    val dobject = DObject.findOne(MongoDBObject("name" -> "A test object")).get

    val req = getAuthenticated
    req.method = "POST"
    val response: Response = FunctionalTest.POST(req, "/bob/link/freeText/object/%s".format(dobject._id.toString), Map("label" -> "toto"), Map.empty[String, File])

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

  it should "not create duplicates of links" in {
    val dobject = DObject.findOne(MongoDBObject("name" -> "A test object")).get

    val req = getAuthenticated
    req.method = "POST"
    val response: Response = FunctionalTest.POST(req, "/bob/link/freeText/object/%s".format(dobject._id.toString), Map("label" -> "toto"), Map.empty[String, File])

    response.status should be (200)

    val links = Link.find(MongoDBObject("value.label" -> "toto"))
    links.size should be (1)

    val updatedObject = DObject.findOneByID(dobject._id).get
    updatedObject.links.size should be (1)
  }

  it should "remove a label from an object" in {
    val dobject = DObject.findOne(MongoDBObject("name" -> "A test object")).get
    val link = Link.findOne(MongoDBObject("value.label" -> "toto")).get

    val req = getAuthenticated
    req.method = "DELETE"
    val response: Response = FunctionalTest.DELETE(req, "/bob/object/%s/link/freeText/%s".format(dobject._id.toString, link._id.toString))

    response.status should be (200)

    Link.count(MongoDBObject("value.label" -> "toto")) should equal (0)

    val updatedObject = DObject.findOneByID(dobject._id).get
    updatedObject.links.size should equal (0)
  }

  it should "add a place link to an object and store some additional information" in {
    val dobject = DObject.findOne(MongoDBObject("name" -> "A test object")).get

    val req = getAuthenticated
    req.method = "POST"
    val response: Response = FunctionalTest.POST(req, "/bob/object/%s/link/place/place/42".format(dobject._id.toString), Map("label" -> "Earth", "geonameID" -> "42"), Map.empty[String, File])

    response.status should be (200)
    Link.count(MongoDBObject("value.label" -> "Earth", "value.geonameID" -> "42", "linkType" -> Link.LinkType.PLACE)) should equal (1)

    val embedded = DObject.findOneByID(dobject._id).get.links.head
    embedded.userName should equal ("bob")
    embedded.linkType should equal (Link.LinkType.PLACE)
    embedded.value("label") should be ("Earth")
    embedded.value("geonameID") should be ("42")
  }

  it should "create a link to an MDR" in {
    val uCol = UserCollection.findOne(MongoDBObject("description" -> "This is a test collection")).get
    val req = getAuthenticated
    req.method = "POST"

    // /{orgId}/object/{spec}/{recordId}/link/{id}
    val response: Response = FunctionalTest.POST(req, "/delving/object/Verzetsmuseum/00001/link/partOf/collection/%s".format(uCol._id.toString), Map.empty[String, String], Map.empty[String, File])
    response.status should be (200)

    val links = Link.find(MongoDBObject("linkType" -> Link.LinkType.PARTOF, "from.uri" -> "http://id.localhost/thing/delving_Verzetsmuseum_00001", "to.id" -> uCol._id))
    val linksList = links.toList
    linksList.length should be (1)
    val theLink = linksList.head

    val mdr = DataSet.getRecord("delving:Verzetsmuseum:00001", "icn")
    mdr should not be (None)
    mdr.get.links.size should be (1)
    val embedded = mdr.get.links.head
    embedded.link should equal (theLink._id)
    embedded.linkType should be (Link.LinkType.PARTOF)
    embedded.userName should be ("bob")
  }

  it should "find the linked MDR in SOLR " in {
    val request = getAuthenticated

    val testTheme = ThemeHandler.getDefaultTheme
    testTheme should not be (None)

    val userCollection = UserCollection.findOne(MongoDBObject("description" -> "This is a test collection")).get
    val mdrListItemList: List[ListItem] = Search.search(None, request, testTheme.get, List("%s:%s OR %s:%s %s:%s".format(RECORD_TYPE, OBJECT, RECORD_TYPE, MDR, COLLECTIONS, userCollection._id)))._1

    mdrListItemList.size should be (1)
  }

  it should "remove a link to an MDR" in {
    val uCol = UserCollection.findOne(MongoDBObject("description" -> "This is a test collection")).get

    val links = Link.find(MongoDBObject("linkType" -> Link.LinkType.PARTOF, "from.uri" -> "http://id.localhost/thing/delving_Verzetsmuseum_00001", "to.id" -> uCol._id))
    links.size should be (1)

    val req = getAuthenticated
    req.method = "POST"

    val response: Response = FunctionalTest.DELETE(req, "/delving/object/Verzetsmuseum/00001/link/partOf/collection/%s".format(uCol._id.toString))
    response.status should be (200)

    val mdr = DataSet.getRecord("delving:Verzetsmuseum:00001", "icn")
    mdr should not be (None)
    mdr.get.links.size should be (0)

    val newLinks = Link.find(MongoDBObject("linkType" -> Link.LinkType.PARTOF, "from.uri" -> "http://id.localhost/thing/delving_Verzetsmuseum_00001", "to.id" -> uCol._id))
    newLinks.size should be (0)
  }

  it should "block and unblock a Link to an object" in {

    val dobject = DObject.findOne(MongoDBObject("name" -> "Another test object")).get
    val uCol = UserCollection.findOne(MongoDBObject("description" -> "This is a test collection")).get

    // create Link between from the object to the collection
    Scope.Session.current().put("userName", "bob")
    val l = DObjects.createCollectionLink(uCol._id, dobject._id, "localhost")

    Link.blockLinks(OBJECT, dobject._id, "bob", true)

    // the Link should be blocked
    val blocked = Link.findOneByID(l._1.get).get
    blocked.blocked should be (true)
    
    // the embedded links should be marked as blocked
    UserCollection.findOneByID(uCol._id).get.links.find(_.link == blocked._id).get.blocked should be (true)
    DObject.findOneByID(dobject._id).get.links.find(_.link == blocked._id).get.blocked should be (true)

    // unblock, not via HTTP yet
    Link.blockLinks(OBJECT, dobject._id, "bob", false)

    // the Link should be unblocked
    val unblocked = Link.findOneByID(l._1.get).get
    unblocked.blocked should be (false)

    // the embedded links should be marked as blocked
    UserCollection.findOneByID(uCol._id).get.links.find(_.link == unblocked._id).get.blocked should be (false)
    DObject.findOneByID(dobject._id).get.links.find(_.link == unblocked._id).get.blocked should be (false)

  }

  /*
  it should "fail if an unexisting label tries to be removed" in {
    val dobject = DObject.findOne(MongoDBObject()).get

    val req = getAuthenticated
    req.method = "DELETE"
    val response: Response = FunctionalTest.DELETE(req, "/bob/object/%s/label/%s".format(dobject._id.toString, "somethingSomething"))

    response.status should be (500)
  }
  */

  def getAuthenticated = {
    val login = FunctionalTest.POST("/login", Map("username" -> "bob", "password" -> "secret"))
    val req = FunctionalTest.newRequest()
    req.cookies = login.cookies
    req
  }

}