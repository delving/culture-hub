import models.mongoContext._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Cleanup {

  def cleanup {
    connection.dropDatabase()
//    IndexingService.deleteByQuery("*:*")
  }

}