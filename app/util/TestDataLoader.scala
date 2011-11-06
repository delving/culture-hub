package util

import models.salatContext._
import com.mongodb.casbah.Imports._
import models._
import controllers.AccessControl

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

/**
 * Generic TestData set-up. This only makes sure the test database is empty at the beginning of the test run
 */
trait TestData {
  // clean everything up when we start
  connection.getCollectionNames() foreach {
    collection =>
      connection.getCollection(collection).remove(MongoDBObject())
  }
}

trait TestDataGeneric extends TestData {
  YamlLoader.load[List[Any]]("testData.yml").foreach {
    _ match {
      case u: User => User.insert(u.copy(password = play.libs.Crypto.passwordHash(u.password)))
      case d: DataSet => DataSet.insert(d)
      case md: MetadataRecord => {
        val ds = DataSet.findBySpec("Verzetsmuseum").get
        DataSet.getRecords(ds).insert(md)
      }
      case _ =>
    }
  }

  val delving = Organization(node = "cultureHub", orgId = "delving", name = Map("en" -> "Delving"))
  val bnf = Organization(node = "cultureHub", orgId = "bnf", name = Map("en" -> "National Library of France", "fr" -> "Bibliothèque nationale de France"))

  val delvingId = Organization.insert(delving)
  val bnfId = Organization.insert(bnf)

  // all users are in delving
  val pushDelving = $set ("organizations.delving" -> delvingId.get)
  User.update(MongoDBObject(), pushDelving)

  val delvingOwners = Group(node = "cultureHub", name = "Owners", orgId = delving.orgId, grantType = GrantType.OWN)
  val delvingOwnersId = Group.insert(delvingOwners)

  // bob is an owner
  val bob = User.findByUsername("bob").get._id
  AccessControl.addToGroup(bob, delvingOwnersId.get)


}

class TestDataLoader extends TestDataGeneric
