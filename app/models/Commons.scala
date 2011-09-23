package models

import com.novus.salat
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import salat.dao.{SalatMongoCursor, SalatDAO}
import java.util.regex.Pattern

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Commons[A <: salat.CaseClass] { self: AnyRef with SalatDAO[A, ObjectId] =>

  RegisterJodaTimeConversionHelpers()

  def findByUser(id: ObjectId) = find(MongoDBObject("user_id" -> id))
  def findAllWithIds(ids: List[ObjectId]) = find(("_id" $in ids))
  def findAll = find(MongoDBObject())
  def findRecent(howMany: Int) = find(MongoDBObject()).sort(MongoDBObject("TS_update" -> -1)).limit(howMany)

  def queryAll(query: String) = if(queryOk(query)) find(MongoDBObject("name" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE))) else findAll
  def queryWithUser(query: String, id: ObjectId) = if(queryOk(query)) find(MongoDBObject("user_id" -> id, "name" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE))) else findByUser(id)
  def queryOk(query: String) = query != null && query.trim().length > 0
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