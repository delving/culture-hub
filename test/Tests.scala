package test

import cake.MetadataModelComponent
import com.borachio.scalatest.MockFactory
import com.mongodb.casbah.commons.MongoDBObject
import eu.delving.metadata.MetadataModel
import org.scalatest.matchers._
import org.scalatest.Suite
import models.salatContext._
import play.test._
import play.libs.OAuth2
import play.libs.OAuth2.Response
import util.{YamlLoader, ThemeHandler, ThemeHandlerComponent}
import models.{DataSet, AccessRight, PortalTheme, User}
import test.{TestDataDatasets, TestEnvironment, TestDataGeneric, TestData}

/**
 * General test environment. Wire-in components needed for tests here and initialize them with Mocks IF THEY ARE MOCKABLE (e.g. the ThemeHandler is not)
 */
trait TestEnvironment extends ThemeHandlerComponent with MetadataModelComponent with Suite with MockFactory {
  val metadataModel: MetadataModel = mock[MetadataModel]
  val themeHandler: ThemeHandler = new ThemeHandler // mock[ThemeHandler]
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

trait TestDataGeneric extends TestData {
  YamlLoader.load[List[Any]]("testData.yml").foreach {
    _ match {
      case u: User => User.insert(u.copy(password = play.libs.Crypto.passwordHash(u.password)))
      case _ =>
    }
  }
}

trait TestDataDatasets extends TestData {
  try {
    YamlLoader.load[List[Any]]("testDataSets.yml").foreach {
      _ match {
        case d: DataSet => DataSet.insert(d)
        case _ =>
      }
    }
  } catch {
    case e: Throwable => e.printStackTrace(); throw (e)
  }
}

class TestDataLoader extends TestDataGeneric with TestDataDatasets

/**
 * Test for the ThemeHandler. We use UnitFlatSpec which is a Play version of the FlatSpec
 */
class ThemeHandlerTests extends UnitFlatSpec with ShouldMatchers with TestDataGeneric with TestEnvironment {

  override val themeHandler = new ThemeHandler

  it should "load themes from disk into the database" in {
    themeHandler.startup()
    themeHandler.hasSingleTheme should be(false)
    PortalTheme.findAll.size should not be (0)
  }

  it should "deliver a default theme" in {
    themeHandler.getDefaultTheme should not be (null)
  }

}

/**
 * This test is tricky to run. In dev mode, play runs on a single thread so making a request to itself does not work and you
 * get a timeout after one minute. To run this test I fire up another instance on port 9001.
 */
class OAuth2TokenEndPointTest extends UnitFlatSpec with ShouldMatchers with TestDataGeneric with TestEnvironment {
  override val themeHandler = new ThemeHandler

  it should "be able to authenticate clients" in {
    val cultureHubEndPoint = new OAuth2("http://localhost:9001/authorize", "http://localhost:9001/token", "bob@gmail.com", "secret")
    val response: Response = cultureHubEndPoint.retrieveAccessToken()
    response.error should be(null)
    response.accessToken should not be (null)
  }

  it should "deny invalid login attempts" in {
    val cultureHubEndPoint = new OAuth2("http://localhost:9001/authorize", "http://localhost:9001/token", "bob@gmail.com", "wrongSecret")
    val response: Response = cultureHubEndPoint.retrieveAccessToken()
    response.error should not be (null)
    response.accessToken should be(null)

  }
}

class AccessControlSpec extends UnitFlatSpec with ShouldMatchers with TestDataGeneric with TestDataDatasets {

  it should "tell if a user has read access" in {
    DataSet.canRead("jimmy", "cultureHub") should be(true)
    DataSet.canRead("bob", "cultureHub") should be(true)
  }
  it should "tell if a user has create access" in {
    DataSet.canCreate("jimmy", "cultureHub") should be(true)
    DataSet.canCreate("bob", "cultureHub") should be(false)
  }
  it should "tell if a user has update access" in {
    DataSet.canUpdate("jimmy", "cultureHub") should be(true)
    DataSet.canUpdate("bob", "cultureHub") should be(false)
  }
  it should "tell if a user has delete access" in {
    DataSet.canDelete("jimmy", "cultureHub") should be(true)
    DataSet.canDelete("bob", "cultureHub") should be(false)
  }
  it should "tell if a user owns the object" in {
    DataSet.owns("jimmy", "cultureHub") should be(true)
    DataSet.owns("bob", "cultureHub") should be(false)
  }

  it should "update rights of an existing user" in {
    DataSet.canCreate("bob", "cultureHub") should be(false)
    DataSet.canUpdate("bob", "cultureHub") should be(false)
    DataSet.addAccessRight("bob", "cultureHub", create = true, update = true)
    DataSet.canCreate("bob", "cultureHub") should be(true)
    DataSet.canUpdate("bob", "cultureHub") should be(true)
    DataSet.canRead("bob", "cultureHub") should be(true)
    DataSet.owns("bob", "cultureHub") should be(false)
  }

  it should "add rights for a non-existing user" in {
    DataSet.canRead("jane", "cultureHub") should be(false)
    DataSet.addAccessRight("jane", "cultureHub", read = true)
    DataSet.canRead("jane", "cultureHub") should be(true)
    DataSet.canCreate("jane", "cultureHub") should be(false)
    DataSet.canUpdate("jane", "cultureHub") should be(false)
    DataSet.canDelete("jane", "cultureHub") should be(false)
    DataSet.owns("jane", "cultureHub") should be(false)
  }

}