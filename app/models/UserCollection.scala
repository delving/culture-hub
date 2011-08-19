package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class UserCollection(_id: ObjectId, name: String, node: String, description: Option[String], access: AccessRight) extends Repository

object UserCollection extends SalatDAO[UserCollection, ObjectId](collection = userCollectionsCollection)