package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import java.util.Date

/**
 * Draft Label
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Label(_id: ObjectId = new ObjectId,
                 TS_update: Date,
                 user_id: ObjectId,
                 userName: String,
                 name: String = "",
                 description: String = "",
                 visibility: Visibility = Visibility.PUBLIC,
                 deleted: Boolean = false,
                 thumbnail_id: Option[ObjectId] = None,
                 labelType: String,
                 value: String) extends Thing

object Label extends SalatDAO[Label, ObjectId](labelsCollection) with Commons[Label] with Pager[Label] with Resolver[Label]