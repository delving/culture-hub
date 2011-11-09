package models

import com.novus.salat.dao.SalatDAO
import com.novus.salat
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Resolver[A <: salat.CaseClass] { self: AnyRef with SalatDAO[A, ObjectId] =>

  def findByIdUnsecured(id: String): Option[A] = {
    id match {
      case null => None
      case objectId if !ObjectId.isValid(objectId) => None
      case objectId => findOne(MongoDBObject("_id" -> new ObjectId(id), "deleted" -> false))
    }
  }

  def findById(id: String, user: ObjectId): Option[A] = {
    id match {
      case null => None
      case objectId if !ObjectId.isValid(objectId) => None
      case objectId => findOne(MongoDBObject("_id" -> new ObjectId(id), "deleted" -> false) ++ $or("user_id" -> user, "contributors" -> user))
    }
  }

}