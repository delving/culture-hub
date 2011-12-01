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
                 value: Map[String, String]) {

}

object Link extends SalatDAO[Link, ObjectId](linksCollection) {

  val LABEL = "label" // the label / name of a link

  object LinkType {
    val FREETEXT = "freeText"
    val PLACE = "place"
    val PARTOF = "partOf"
  }

  def create(linkType: String, userName: String, value: Map[String, String], from: LinkReference, to: LinkReference): (Option[ObjectId], Link) = {
    val link = Link(userName = userName, linkType = linkType, value = value, from = from, to = to)
    (Link.insert(link), link)
  }

}

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
case class EmbeddedLink(TS: Date = new Date(), userName: String, linkType: String, link: ObjectId, value: Map[String,  String])
