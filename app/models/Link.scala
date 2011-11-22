package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import java.util.Date

case class Link(_id: ObjectId = new ObjectId,
                 userName: String, // user who created the label in the first place
                 linkType: String, // internal type of the link: freeText, place
                 from: LinkReference,
                 to: LinkReference,
                 value: LinkValue)

object Link extends SalatDAO[Link, ObjectId](labelsCollection) {

  def create(linkType: String, userName: String, value: LinkValue, from: LinkReference, to: LinkReference): Option[ObjectId] = {
    val link = linkType match {
      case "freeText" => Link(userName = userName, linkType = linkType, value = value, from = from, to = to)
      case "place" => Link(userName = userName, linkType = linkType, value = value, from = from, to = to)
      case _ => return None
    }

    Link.insert(link)
  }

}

/**
 * The "value" of a Link, i.e. its label. Can contain more than just one text (e.g. additional payload data),
 * for performance reasons.
 */
case class LinkValue(label: String)


/**
 * An arrow in a link. This is flexible: if we have a hubType we can lookup by mongo id, or else we have a uri.
 */
case class LinkReference(id: Option[ObjectId] = None, // mongo id for hub-based reference
                         uri: Option[String] = None, // URI
                         hubType: Option[String] = None, // internal CH type of the reference (DObject, ...)
                         refType: Option[String] = None) // external type of the reference


/**
 * A denormalized bit of a link that lives in a linked object. Makes it easy to do lookups.
 */
case class EmbeddedLink(TS: Date = new Date(), userName: String, link: ObjectId, value: LinkValue)
