/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

import com.novus.salat
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import salat.dao.{SalatMongoCursor, SalatDAO}
import Commons.FilteredMDO

/**
 * Common trait to be used with the companion object of a Thing
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait Commons[A <: Thing] { self: AnyRef with SalatDAO[A, ObjectId] =>

  def FilteredMDO[A <: String, B](elems : Tuple2[A, B]*) = MongoDBObject(elems.toList) ++ MongoDBObject("deleted" -> false)

  def idVisibilityQuery(who: ObjectId) = MongoDBObject("$or" -> List(MongoDBObject("visibility.value" -> Visibility.PUBLIC.value), MongoDBObject("visibility.value" -> Visibility.PRIVATE.value, "user_id" -> who)))
  def visibilityQuery(who: String) = MongoDBObject("$or" -> List(MongoDBObject("visibility.value" -> Visibility.PUBLIC.value), MongoDBObject("visibility.value" -> Visibility.PRIVATE.value, "userName" -> who)))

  def browseByUser(id: ObjectId, whoBrowses: ObjectId) = find(FilteredMDO("user_id" -> id) ++ idVisibilityQuery(whoBrowses))
  def browseAll(whoBrowses: ObjectId) = find(FilteredMDO() ++ idVisibilityQuery(whoBrowses))

  def findAllWithIds(ids: List[ObjectId]) = find(("_id" $in ids))
  def findRecent(howMany: Int) = find(FilteredMDO("visibility.value" -> Visibility.PUBLIC.value)).sort(MongoDBObject("TS_update" -> -1)).limit(howMany)
  def findByUser(userName: String) = find(FilteredMDO("userName" -> userName))

  def findVisibleByUser(userName: String, whoBrowses: ObjectId) = find(FilteredMDO("userName" -> userName) ++ idVisibilityQuery(whoBrowses))

  def findByIdSecured(id: ObjectId, userName: String) = findOne(FilteredMDO("_id" -> id) ++ visibilityQuery(userName))

  def owns(user: ObjectId, id: ObjectId) = count(MongoDBObject("_id" -> id, "user_id" -> user)) > 0

  def delete(id: ObjectId) {
    update(MongoDBObject("_id" -> id), $set ("deleted" -> true), false, false)
  }

  def fetchName(id: String, collection: MongoCollection): String = collection.findOne(MongoDBObject("_id" -> new ObjectId(id)), MongoDBObject("name" -> 1)) match {
    case None => ""
    case Some(dbo) => dbo.get("name").toString
  }

}

object Commons {

  def FilteredMDO[A <: String, B](elems : Tuple2[A, B]*) = MongoDBObject(elems.toList) ++ MongoDBObject("deleted" -> false)

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
      val c = list.slice((p - 1) * pageSize, (p - 1) * pageSize + pageSize)
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
      val c = cursor.skip((p - 1) * pageSize).limit(pageSize)
      (c.toList, c.count)
    }
  }
}