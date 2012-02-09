import com.mongodb.casbah.commons.MongoDBObject
import core.indexing.IndexingService
import org.specs2.specification.Before
import models.mongoContext._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Cleanup extends Before {

  def before {
    connection.getCollectionNames() foreach {
        collection =>
          connection.getCollection(collection).remove(MongoDBObject())
      }
      commonsConnection.getCollectionNames() foreach {
        collection =>
          commonsConnection.getCollection(collection).remove(MongoDBObject())
      }

      IndexingService.deleteByQuery("*:*")

  }

}
