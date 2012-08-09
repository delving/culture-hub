package controllers

import org.bson.types.ObjectId


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait CoreImplicits {

  // ~~~ ObjectId

  implicit def oidToString(oid: ObjectId): String = oid.toString

  implicit def oidOptionToString(oid: Option[ObjectId]): String = oid match {
    case Some(id) => id.toString
    case None => ""
  }

  implicit def stringToOidOption(id: String): Option[ObjectId] = ObjectId.isValid(id) match {
    case true => Some(new ObjectId(id))
    case false => None
  }


}
