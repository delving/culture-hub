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
import salat.dao.SalatDAO

/**
 * Common trait to be used with the companion object of a Thing
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait Commons[A <: Thing] { self: AnyRef with SalatDAO[A, ObjectId] =>

  def FilteredMDO[A <: String, B](elems : Tuple2[A, B]*) = MongoDBObject(elems.toList) ++ MongoDBObject("deleted" -> false, "blocked" -> false)

  def visibilityQuery(userName: String) = MongoDBObject("$or" -> List(MongoDBObject("visibility.value" -> Visibility.PUBLIC.value), MongoDBObject("visibility.value" -> Visibility.PRIVATE.value, "userName" -> userName)))

  def browseByUser(userName: String, whoBrowses: String) = find(FilteredMDO("userName" -> userName) ++ visibilityQuery(whoBrowses))

  def findAllWithIds(ids: List[ObjectId]) = find(("_id" $in ids) ++ FilteredMDO() )
  def findRecent(howMany: Int) = find(FilteredMDO("visibility.value" -> Visibility.PUBLIC.value)).sort(MongoDBObject("TS_update" -> -1)).limit(howMany)
  def findByUser(userName: String) = find(FilteredMDO("userName" -> userName))

  def findVisibleByUser(userName: String, whoBrowses: String) = find(FilteredMDO("userName" -> userName) ++ visibilityQuery(whoBrowses))

  def findByIdSecured(id: ObjectId, userName: String) = findOne(FilteredMDO("_id" -> id) ++ visibilityQuery(userName))

  def owns(userName: String, id: ObjectId) = count(MongoDBObject("_id" -> id, "userName" -> userName)) > 0

  def delete(id: ObjectId) {
    update(MongoDBObject("_id" -> id), $set ("deleted" -> true), false, false)
  }

  def fetchName(id: String, collection: MongoCollection): String = collection.findOne(MongoDBObject("_id" -> new ObjectId(id)), MongoDBObject("name" -> 1)) match {
    case None => ""
    case Some(dbo) => dbo.get("name").toString
  }

}