package test

import cake.MetadataModelComponent
import com.borachio.scalatest.MockFactory
import eu.delving.metadata.MetadataModel
import org.scalatest.matchers._
import play.test._
import play.libs.OAuth2
import play.libs.OAuth2.Response
import util._
import models._
import org.scalatest.{BeforeAndAfterAll, Spec, Suite}

/**
 * General test environment. Wire-in components needed for tests here and initialize them with Mocks IF THEY ARE MOCKABLE (e.g. the ThemeHandler is not)
 */
trait TestEnvironment extends ThemeHandlerComponent with MetadataModelComponent with Suite with MockFactory {
  val metadataModel: MetadataModel = mock[MetadataModel]
  val themeHandler: ThemeHandler = new ThemeHandler // mock[ThemeHandler]
}

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

class AccessControlSpec extends UnitFlatSpec with ShouldMatchers with TestDataGeneric {

  it should "tell if a user has read access" in {
    DataSet.canRead("Verzetsmuseum", "jimmy", "cultureHub") should be(true)
    DataSet.canRead("Verzetsmuseum", "bob", "cultureHub") should be(true)
  }
  it should "tell if a user has update access" in {
    DataSet.canUpdate("Verzetsmuseum", "jimmy", "cultureHub") should be(true)
    DataSet.canUpdate("Verzetsmuseum", "bob", "cultureHub") should be(false)
  }
  it should "tell if a user has delete access" in {
    DataSet.canDelete("Verzetsmuseum", "jimmy", "cultureHub") should be(true)
    DataSet.canDelete("Verzetsmuseum", "bob", "cultureHub") should be(false)
  }
  it should "tell if a user owns the object" in {
    DataSet.owns("Verzetsmuseum", "jimmy", "cultureHub") should be(true)
    DataSet.owns("Verzetsmuseum", "bob", "cultureHub") should be(false)
  }

  it should "update rights of an existing user" in {
    DataSet.canDelete("Verzetsmuseum", "bob", "cultureHub") should be(false)
    DataSet.canUpdate("Verzetsmuseum", "bob", "cultureHub") should be(false)
    DataSet.addAccessRight("Verzetsmuseum", "bob", "cultureHub", "delete" -> true, "update" -> true)
    DataSet.canDelete("Verzetsmuseum", "bob", "cultureHub") should be(true)
    DataSet.canUpdate("Verzetsmuseum", "bob", "cultureHub") should be(true)
    DataSet.canRead("Verzetsmuseum", "bob", "cultureHub") should be(true)
    DataSet.owns("Verzetsmuseum", "bob", "cultureHub") should be(false)
  }

  it should "add rights for a non-existing user" in {
    DataSet.canRead("Verzetsmuseum", "jane", "cultureHub") should be(false)
    DataSet.addAccessRight("Verzetsmuseum", "jane", "cultureHub", "read" -> true)
    DataSet.canRead("Verzetsmuseum", "jane", "cultureHub") should be(true)
    DataSet.canUpdate("Verzetsmuseum", "jane", "cultureHub") should be(false)
    DataSet.canDelete("Verzetsmuseum", "jane", "cultureHub") should be(false)
    DataSet.owns("Verzetsmuseum", "jane", "cultureHub") should be(false)
  }

  it should "tell if a user has read access via a group" in {
    DataSet.canRead("Verzetsmuseum", "dan", "cultureHub") should be (true)
  }

}

class DataSetSpec extends UnitFlatSpec with ShouldMatchers with TestDataGeneric with BeforeAndAfterAll {
  import models.DataSet

  val ds = DataSet.find("Verzetsmuseum").get

  override def beforeAll() {
    DataSet.deleteFromSolr(ds)
  }

  override def afterAll() {
    DataSet.deleteFromSolr(ds)
  }

  it should "should Index every entry in the dataset" in {
    DataSet.getRecords(ds).count() should equal (3)
    val outputCount = DataSet.indexInSolr(ds, "icn")
    outputCount should equal ((3, 0))
  }
}