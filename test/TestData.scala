import core.indexing.IndexingService
import models.mongoContext._
import play.api.test._
import play.api.test.Helpers._
import util.TestDataLoader


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait TestData {

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
    //    IndexingService.deleteByQuery("*:*")
  }

  def loadStandalone {
    running(FakeApplication()) {
      load
    }

  }

}