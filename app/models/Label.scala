package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._

/**
 * Draft Label
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Label(_id: ObjectId = new ObjectId, user_id: ObjectId, userName: String, labelType: String, value: String)

object Label extends SalatDAO[Label, ObjectId](labelsCollection) with Commons[Label] with Pager[Label] with Resolver[Label]