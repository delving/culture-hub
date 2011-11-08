package models

import com.novus.salat
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import salat.dao.{SalatMongoCursor, SalatDAO}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Commons[A <: salat.CaseClass] { self: AnyRef with SalatDAO[A, ObjectId] =>

  def visibilityQuery(who: ObjectId) = MongoDBObject("$or" -> List(MongoDBObject("visibility.value" -> Visibility.PUBLIC.value), MongoDBObject("visibility.value" -> Visibility.PRIVATE.value, "user_id" -> who)))

  def browseByUser(id: ObjectId, whoBrowses: ObjectId) = find(MongoDBObject("user_id" -> id) ++ visibilityQuery(whoBrowses))
  def browseAll(whoBrowses: ObjectId) = find(visibilityQuery(whoBrowses))

  def findAllWithIds(ids: List[ObjectId]) = find(("_id" $in ids))
  def findRecent(howMany: Int) = find(MongoDBObject("visibility" -> Visibility.PUBLIC.value)).sort(MongoDBObject("TS_update" -> -1)).limit(howMany)
}

trait Pager[A <: salat.CaseClass] { self: AnyRef with SalatDAO[A, ObjectId] =>

  import views.context.PAGE_SIZE

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