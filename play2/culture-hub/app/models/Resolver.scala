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

import com.novus.salat.dao.SalatDAO
import com.novus.salat
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import models.Commons.FilteredMDO

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Resolver[A <: salat.CaseClass] { self: AnyRef with SalatDAO[A, ObjectId] =>

  def findByIdUnsecured(id: String): Option[A] = {
    id match {
      case null => None
      case objectId if !ObjectId.isValid(objectId) => None
      case objectId => findOne(FilteredMDO("_id" -> new ObjectId(id)))
    }
  }

  def findById(id: String, user: ObjectId): Option[A] = {
    id match {
      case null => None
      case objectId if !ObjectId.isValid(objectId) => None
      case objectId => findOne(FilteredMDO("_id" -> new ObjectId(id)) ++ $or("user_id" -> user, "contributors" -> user))
    }
  }

}