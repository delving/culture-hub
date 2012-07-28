import core.HubServices
import core.indexing.IndexingService
import models.DataSet
import models.mongoContext._
import play.api.mvc.{AsyncResult, Result}
import play.api.test._
import play.api.test.Helpers._
import util.TestDataLoader
import xml.XML


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait TestContext {

  def asyncToResult(response: Result) = response.asInstanceOf[AsyncResult].result.await.get

  def contentAsXML(response: Result) = XML.loadString(contentAsString(response))

  def withTestConfig[T](block: => T) = {
    running(FakeApplication(additionalConfiguration = Map("solr.baseUrl" -> "http://localhost:8983/solr/test"))) {
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
      connection.dropDatabase()
      try {
        DataSet.dao("delving").findBySpecAndOrgId("PrincessehofSample", "delving").map {
          set =>
            HubServices.basexStorage.withSession(set) {
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