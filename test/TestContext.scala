import core.storage.BaseXStorage
import models.mongoContext._
import play.api.mvc.{AsyncResult, Result}
import play.api.test._
import play.api.test.Helpers._
import util.TestDataLoader


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait TestContext {

  def asyncToResult(response: Result) = response.asInstanceOf[AsyncResult].result.await.get

  def withTestContext[T](block: => T) = {
    running(FakeApplication()) {
      load
      try {
        block
      } finally {
        cleanup
      }
    }
  }

  def load {
    TestDataLoader.load()
  }

  def cleanup {
    connection.dropDatabase()
    try {
      BaseXStorage.withSession(core.storage.Collection("delving", "PrincessehofSample")) {
        session => session.execute("drop database delving____PrincessehofSample")
      }
    } catch {
      case _ => //ignore if not found
    }
    // IndexingService.deleteByQuery("*:*")
  }

  def loadStandalone {
    running(FakeApplication()) {
      load
    }

  }

}