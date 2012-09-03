import core.collection.AggregatingOrganizationCollectionLookup
import core.HubServices
import core.indexing.IndexingService
import java.io.File
import models.HubMongoContext._
import org.specs2.mutable.Specification
import play.api.mvc.{AsyncResult, Result}
import play.api.test._
import play.api.test.Helpers._
import _root_.util.{TestDataLoader, DomainConfigurationHandler}
import xml.XML


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait TestContext {

  def asyncToResult(response: Result) = response.asInstanceOf[AsyncResult].result.await.get

  def contentAsXML(response: Result) = XML.loadString(contentAsString(response))

  def applicationPath = if(new File(".").getAbsolutePath.endsWith("culture-hub")) new File(".") else new File("culture-hub")

  def withTestConfig[T](block: => T) = {
    running(FakeApplication(path = applicationPath)) {
      block
    }
  }

  def withTestData[T](block: => T): T = {
    withTestConfig {
      load()
      try {
        block
      } finally {
        cleanup()
      }
    }
  }

  def load() {
    TestDataLoader.load()
  }

  def cleanup() {
    withTestConfig {
      HubServices.init()
      implicit val configuration = DomainConfigurationHandler.getByOrgId("delving")
      createConnection(configuration.mongoDatabase).dropDatabase()
      createConnection(configuration.objectService.fileStoreDatabaseName).dropDatabase()
      createConnection(configuration.objectService.imageCacheDatabaseName).dropDatabase()
      try {
        AggregatingOrganizationCollectionLookup.findBySpecAndOrgId("PrincessehofSample", "delving").map {
          set =>
            HubServices.basexStorage(configuration).withSession(set) {
              session => {
                val r = session.execute("drop database delving____PrincessehofSample")
                println(r)
              }
            }
        }
      } catch {
        case t: Throwable => //ignore if not found
      }
      IndexingService.deleteByQuery("*:*")
    }
  }

  def loadStandalone() {
    running(FakeApplication(path = applicationPath)) {
      load()
    }

  }

}

trait Specs2TestContext extends Specification with TestContext {

  args(sequential = true)

}