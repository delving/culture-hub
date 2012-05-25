import core.indexing.IndexingService
import core.storage.BaseXStorage
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
    connection.dropDatabase()
    try {
      BaseXStorage.withSession(core.storage.Collection("delving", "PrincessehofSample")) {
        session => {
          val r = session.execute("drop database delving____PrincessehofSample")
          println(r)
        }
      }
    } catch {
      case _ => //ignore if not found
    }
    withTestConfig {
      IndexingService.deleteByQuery("*:*")
    }
  }

  def loadStandalone() {
    running(FakeApplication()) {
      load()
    }

  }

}