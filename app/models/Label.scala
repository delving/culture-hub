package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import java.util.Date
import com.mongodb.casbah.Imports._

/**
 * Instance of a Label.
 * Labels are stored in one separate mongo collection. When a thing is labelled, a reference to the label is saved in the thing and a reference to the thing is saved in the label.
 * That way we can always do performant lookups in mongo.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Label(_id: ObjectId = new ObjectId,
                 TS_update: Date,
                 userName: String, // user who created the label in the first place
                 references: List[LabelReference] = List.empty[LabelReference],
                 labelType: String, // internal type of the label: freeText, place
                 value: String,
                 geonameId: Option[String] = None)

object Label extends SalatDAO[Label, ObjectId](labelsCollection) {

  def create(labelType: String, userName: String, value: String, geonameId: Option[String] = None): Option[ObjectId] = {
    val label = labelType match {
      case "freeText" => Label(TS_update = new Date(), userName = userName, labelType = labelType, value = value)
      case "geoname"  => Label(TS_update = new Date(), userName = userName, labelType = labelType, value = value, geonameId = geonameId)
      case _ => return None
    }

    Label.findOne(MongoDBObject("labelType" -> labelType, "value" -> value)) match {
      case None => Label.insert(label)
      case Some(l) => Some(l._id)
    }

  }

}


/**
 * Format of a Label inside of a Labeled entity
 */
case class EmbeddedLabel(TS: Date = new Date(),
                         userName: String, // the user who did the labeling action
                         label: ObjectId)

/**
 * Format of a Label reference inside of a Label - i.e. marking where a Label is being used
 */
case class LabelReference(TS: Date = new Date(),
                          userName: String, // the user who did the labeling action
                          id: ObjectId, // target id
                          targetType: String) // the class name of the targeted element
