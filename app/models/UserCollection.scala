package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import java.util.Date

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class UserCollection(_id: ObjectId = new ObjectId,
                           TS_update: Date,
                           user_id: ObjectId,
                           userName: String,
                           name: String,
                           description: String,
                           visibility: Visibility,
                           deleted: Boolean = false,
                           thumbnail_id: Option[ObjectId]) extends Thing

object UserCollection extends SalatDAO[UserCollection, ObjectId](userCollectionsCollection) with Commons[UserCollection] with Resolver[UserCollection] with Pager[UserCollection] {

  def fetchName(id: String): String = fetchName(id, userCollectionsCollection)

}