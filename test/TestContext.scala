import core.HubServices
import core.indexing.IndexingService
import models.DataSet
import models.mongoContext._
import play.api.mvc.{AsyncResult, Result}
import play.api.test._
import play.api.test.Helpers._
import util.{DomainConfigurationHandler, TestDataLoader}
import xml.XML


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait TestContext {

  def asyncToResult(response: Result) = response.asInstanceOf[AsyncResult].result.await.get

  def contentAsXML(response: Result) = XML.loadString(contentAsString(response))

  def withTestConfig[T](block: => T) = {
    running(FakeApplication()) {
      block
    }
  }

  def withTestData[T](block: => T) = {
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

  def loadDataSet() {
    TestDataLoader.loadDataSet()
  }

  def cleanup() {
    withTestConfig {
      HubServices.init()
      implicit val configuration = DomainConfigurationHandler.getByOrgId("delving")
      createConnection(configuration.mongoDatabase).dropDatabase()
      try {
        DataSet.dao("delving").findBySpecAndOrgId("PrincessehofSample", "delving").map {
          set =>
            HubServices.basexStorage(configuration).withSession(set) {
              session => {
                val r = session.execute("drop database delving____PrincessehofSample")
                println(r)
              }
            }
        }
      } catch {
        case _ => //ignore if not found
      }
      IndexingService.deleteByQuery("*:*")
    }
  }

  def loadStandalone() {
    running(FakeApplication()) {
      load()
    }

  }

}