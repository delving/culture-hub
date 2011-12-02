package models

import com.novus.salat
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import salat.dao.{SalatMongoCursor, SalatDAO}

/**
 * Common trait to be used with the companion object of a {@link models.Thing }
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait Commons[A <: Thing] { self: AnyRef with SalatDAO[A, ObjectId] =>

  def visibilityQuery(who: ObjectId) = MongoDBObject("$or" -> List(MongoDBObject("visibility.value" -> Visibility.PUBLIC.value), MongoDBObject("visibility.value" -> Visibility.PRIVATE.value, "user_id" -> who)))

  def browseByUser(id: ObjectId, whoBrowses: ObjectId) = find(MongoDBObject("deleted" -> false, "user_id" -> id) ++ visibilityQuery(whoBrowses))
  def browseAll(whoBrowses: ObjectId) = find(MongoDBObject("deleted" -> false) ++ visibilityQuery(whoBrowses))

  def findAllWithIds(ids: List[ObjectId]) = find(("_id" $in ids))
  def findRecent(howMany: Int) = find(MongoDBObject("deleted" -> false, "visibility.value" -> Visibility.PUBLIC.value)).sort(MongoDBObject("TS_update" -> -1)).limit(howMany)
  def findByUser(userName: String) = find(MongoDBObject("deleted" -> false, "userName" -> userName))

  def findPublicByUser(userName: String) = find(MongoDBObject("deleted" -> false, "userName" -> userName, "visibility.value" -> Visibility.PUBLIC.value))

  def owns(user: ObjectId, id: ObjectId) = count(MongoDBObject("_id" -> id, "user_id" -> user)) > 0

  def delete(id: ObjectId) {
    update(MongoDBObject("_id" -> id), $set ("deleted" -> true), false, false)
  }

  def fetchName(id: String, collection: MongoCollection): String = collection.findOne(MongoDBObject("_id" -> new ObjectId(id)), MongoDBObject("name" -> 1)) match {
    case None => ""
    case Some(dbo) => dbo.get("name").toString
  }

}

trait Pager[A <: salat.CaseClass] { self: AnyRef with SalatDAO[A, ObjectId] =>

  import views.context.PAGE_SIZE

  /**
   * Returns a page and the total object count
   * @param page the page number
   * @param pageSize optional size of the page, defaults to PAGE_SIZE
   */
  implicit def listWithPage(list: List[A]) = new {
    def page(page: Int, pageSize: Int = PAGE_SIZE) = {
      val p = if(page == 0) 1 else page
      val c = list.slice((p - 1) * pageSize, (p - 1) * pageSize + PAGE_SIZE)
      (c, list.size)
    }
  }

  implicit def cursorWithPage(cursor: SalatMongoCursor[A]) = new {

    /**
     * Returns a page and the total object count
     * @param page the page number
     * @param pageSize optional size of the page, defaults to PAGE_SIZE
     */
    def page(page: Int, pageSize: Int = PAGE_SIZE) = {
      val p = if(page == 0) 1 else page
      val c = cursor.skip((p - 1) * pageSize).limit(PAGE_SIZE)
      (c.toList, c.count)
    }
  }
}