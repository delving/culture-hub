package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import java.util.Date
import com.mongodb.casbah.Imports._

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

  def create(linkType: String, userName: String, value: Map[String, String], from: LinkReference, to: LinkReference, allowDuplicates: Boolean = false): (Option[ObjectId], Link, Boolean) = {
    val link = Link(userName = userName, linkType = linkType, value = value, from = from, to = to)
    if(allowDuplicates) {
      (Link.insert(link), link, true)
    } else {

      val query = MongoDBObject("userName" -> userName, "linkType" -> linkType, "value" -> value.asDBObject)

      def fields(ref: LinkReference, refName: String) = {
        val builder = MongoDBObject.newBuilder
        ref.id match {
          case Some(id) => builder += (refName + ".id" -> id)
          case None =>
        }
        ref.hubType match {
          case Some(ht) => builder+= (refName + ".hubType" -> ht)
          case None =>
        }
        ref.uri match {
          case Some(uri) => builder += (refName + ".uri" -> uri)
          case None =>
        }
        ref.refType match {
          case Some(refType) => builder += (refType + ".refType" -> refType)
          case None =>
        }
        builder.result()
      }

      val q = query ++ fields(from, "from") ++ fields(to, "to")

      Link.findOne(q) match {
        case Some(l) => (Some(l._id), l, true)
        case None => (Link.insert(link), link, false)
      }
    }
  }

  def findTo(toUri: String, linkType: String) = Link.find(MongoDBObject("linkType" -> linkType, "to.uri" -> toUri))

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
