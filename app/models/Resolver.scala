package models

import com.novus.salat.dao.SalatDAO
import com.novus.salat
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Resolver[A <: salat.CaseClass] { self: AnyRef with SalatDAO[A, ObjectId] =>

  def findById(id: String): Option[A] = {
    id match {
      case null => None
      case objectId if !ObjectId.isValid(objectId) => None
      case objectId => findOne(MongoDBObject("_id" -> new ObjectId(id), "deleted" -> false)) // TODO FIXME ACL
    }
  }

}