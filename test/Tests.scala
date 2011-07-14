package test

import cake.MetadataModelComponent
import com.borachio.scalatest.MockFactory
import com.mongodb.casbah.commons.MongoDBObject
import eu.delving.metadata.MetadataModel
import models.{PortalTheme, User}
import org.scalatest.matchers._
import org.scalatest.Suite
import models.salatContext._
import play.test._
import util.{ThemeHandler, ThemeHandlerComponent}
import test.{TestEnvironment, TestDataUsers, TestData}

/**
 * General test environment. Wire-in components needed for tests here and initialize them with Mocks IF THEY ARE MOCKABLE (e.g. the ThemeHandler is not)
 */
trait TestEnvironment extends ThemeHandlerComponent with MetadataModelComponent with Suite with MockFactory {
  val metadataModel: MetadataModel = mock[MetadataModel]
  val themeHandler: ThemeHandler
}

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

/**
 * Loads test users
 */
trait TestDataUsers extends TestData {
  Yaml[List[Any]]("testUsers.yml").foreach {
    _ match {
      case u: User => User.insert(u)
    }
  }
}

class TestDataUsersLoader extends TestDataUsers

/**
 * Test for the ThemeHandler. We use UnitFlatSpec which is a Play version of the FlatSpec
 */
class ThemeHandlerTests extends UnitFlatSpec with ShouldMatchers with TestDataUsers with TestEnvironment {

  val themeHandler = new ThemeHandler

  it should "load themes from disk into the database" in {
    themeHandler.startup()
    themeHandler.hasSingleTheme should be(false)
    PortalTheme.findAll.size should not be (0)
  }

  it should "deliver a default theme" in {
    themeHandler.getDefaultTheme should not be (null)
  }

}