package util

import models.salatContext._
import com.mongodb.casbah.commons.MongoDBObject._
import models.{DataSet, UserGroup, User, MetadataRecord}
import com.mongodb.casbah.commons.MongoDBObject

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
      case g: UserGroup => UserGroup.insert(g)
      case d: DataSet => DataSet.insert(d)
      case md: MetadataRecord => {
        val ds = DataSet.getWithSpec("Verzetsmuseum")
        DataSet.getRecords(ds).insert(md)
      }
      case _ =>
    }
  }
}

class TestDataLoader extends TestDataGeneric
