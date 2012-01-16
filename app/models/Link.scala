/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

import _root_.util.Constants._
import _root_.util.ProgrammerException
import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import java.util.Date
import com.mongodb.casbah.Imports._
import com.novus.salat._
import play.Logger

case class Link(_id: ObjectId = new ObjectId,
                userName: String, // user who created the label in the first place
                linkType: String, // internal type of the link: freeText, place
                from: LinkReference,
                to: LinkReference,
                value: Map[String, String],
                blocked: Boolean = false,
                blockingInfo: Option[BlockingInfo] = None) {

}

object Link extends SalatDAO[Link, ObjectId](linksCollection) {

  val LABEL = "label"

  // the label / name of a link

  object LinkType {
    val FREETEXT = "freeText"
    val PLACE = "place"
    val PARTOF = "partOf"
    val THUMBNAIL = "thumbnail" // special internal link to denote usage as thumbnail
  }

  /**
   * Creates a link
   *
   * @param linkType        type of the link, see LinkType object
   * @param userName        user name of the creator
   * @param value           value of the link
   * @param from            from where this link comes
   * @param to              to where this link goes
   * @param embedFrom       optional embedded link to be written in the source object
   * @param embedTo         optional embedded link to be written in the target object
   * @param allowDuplicates whether or not this link can exist more than one time (false by default)
   */
  def create(linkType: String, userName: String, value: Map[String, String], from: LinkReference, to: LinkReference,
             embedFrom: Option[EmbeddedLinkWriter] = None, embedTo: Option[EmbeddedLinkWriter] = None,
             allowDuplicates: Boolean = false): (Option[ObjectId], Link, Boolean) = {

    val link = Link(userName = userName, linkType = linkType, value = value, from = from, to = to)
    if (allowDuplicates) {
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
          case Some(ht) => builder += (refName + ".hubType" -> ht)
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
        case None =>

          // sanity check on the embedded stuff
          if (embedFrom != None && ((link.from.hubAlternativeId == None || link.from.hubCollection == None || link.from.hubType == None) && (link.from.hubType == None || link.from.id == None))) {
            throw new ProgrammerException("You can't create a link with an embedFrom if the linkReference has no hubType and id OR hubCollection and hubAlternativeId!")
          }
          if (embedTo != None && ((link.to.hubAlternativeId == None || link.to.hubCollection == None || link.to.hubType == None) && (link.to.hubType == None || link.to.id == None))) {
            throw new ProgrammerException("You can't create a link with an embedTo if the linkReference has no hubType and id OR hubCollection and hubAlternativeId!")
          }

          val inserted = Link.insert(link)

          inserted match {
            case Some(l) =>
              val e = EmbeddedLink(userName = link.userName, linkType = link.linkType, link = l, value = link.value)
              embedFrom match {
                case Some(from) => from.write(e)
                case None => // nope
              }
              embedTo match {
                case Some(to) => to.write(e)
                case None => // nope
              }
            case None =>
              // well this should never be returned, Salat will blow up first
              return (null, null, false)
          }
          (inserted, link, false)
      }
    }
  }

  def removeById(linkId: ObjectId) {
    Link.findOneByID(linkId) match {
      case Some(link) => removeLink(link)
      case None => // it's not there
    }
  }

  def hubTypeToCollection(hubType: String) = Option(hubType match {
    case OBJECT => objectsCollection
    case USERCOLLECTION => userCollectionsCollection
    case STORY => userStoriesCollection
    case USER => userCollection
    case _ => null
  })

  def buildUri(hubType: String, id: String, hostName: String) = {
    hubType match {
      case USER => "http://id.culturecloud.eu/actor/" + id
      case _ =>
        val typeUri = hubType match {
          case OBJECT => "thing/" + id
          case USERCOLLECTION => "collection/" + id
          case STORY => "story/" + id
          case MDR => "thing/" + id
          case _ => throw new ProgrammerException("Invalid hubType " + hubType)
        }
        "http://id.%s/%s".format(hostName, typeUri)
    }
  }

  def removeLink(link: Link) {

    def removeEmbedded(link: ObjectId, hubType: String, id: Option[ObjectId], hubCollection: Option[String], hubAlternativeId: Option[String]) {
      val collection: Option[MongoCollection] = hubTypeToCollection(hubType)
      val pull = $pull("links" -> MongoDBObject("link" -> link))
      collection match {
        case Some(c) =>
          c.update(MongoDBObject("_id" -> id.get), pull)
        case None =>
          if (hubType == MDR && hubCollection.isDefined && hubAlternativeId.isDefined) {
            connection(hubCollection.get).update(MongoDBObject(MDR_HUB_ID -> hubAlternativeId.get), pull)
          } else {
            Logger.warn("Could not delete embedded Link %s %s %s", hubType, id, hubCollection)
          }
      }

    }

    // remove embedded guys
    if ((link.from.hubType != None && link.from.id != None) || (link.from.hubCollection != None && link.from.hubAlternativeId != None && link.from.hubType != None)) {
      removeEmbedded(link._id, link.from.hubType.get, link.from.id, link.from.hubCollection, link.from.hubAlternativeId)
    }
    if ((link.to.hubType != None && link.to.id != None) || (link.to.hubCollection != None && link.to.hubAlternativeId != None && link.to.hubType != None)) {
      removeEmbedded(link._id, link.to.hubType.get, link.to.id, link.to.hubCollection, link.to.hubAlternativeId)
    }

    Link.remove(link)

  }

  /**
   * Blocks all incoming and outgoing links for an object.
   */
  def blockLinks(objectType: String, id: ObjectId, whoBlocks: String, block: Boolean = true) = {

    def updateUGCEmbeddedLinks(grouped: Map[String, (String, List[(ObjectId, ObjectId)])]) {
      grouped foreach {
        typed =>
          val collection = hubTypeToCollection(typed._1).getOrElse(throw new RuntimeException("Unknown type while blocking link"))
          typed._2._2.foreach {
            l =>
              collection.update(MongoDBObject("_id" -> (l._2), "links.link" -> l._1), $set("links.$.blocked" -> block), false, true)
          }
      }
    }

    def updateMDREmbeddedLinks(grouped: Map[String, (String, List[(ObjectId, String)])]) {
      grouped foreach {
        mdrLinks => mdrLinks._2._2.foreach {
          mdrLink =>
            connection(mdrLinks._1).update(MongoDBObject("hubId" -> mdrLink._2, "links.link" -> mdrLink._1), $set("links.$.blocked" -> block), false, true)
        }
      }
    }

    def makeUGCLinkGroup(links: List[Link], select: Link => LinkReference) = {
      links.filter(select(_).hubType != None).groupBy(select(_).hubType.get).transform((key, value) => (key, value.map(link => (link._id, select(link).id.get))))
    }

    def makeMDRLinkGroup(links: List[Link], select: Link => LinkReference) = {
      links.filter(select(_).hubAlternativeId != None).groupBy(select(_).hubCollection.get).transform((key, value) => (key, value.map(link => (link._id, select(link).hubAlternativeId.get))))
    }

    // block / unblock all links to / from this thing
    Link.update(MongoDBObject("to.id" -> id, "to.hubType" -> objectType), $set("blocked" -> block, "blockingInfo" -> grater[BlockingInfo].asDBObject(BlockingInfo(whoBlocks))), false, true)
    Link.update(MongoDBObject("from.id" -> id, "from.hubType" -> objectType), $set("blocked" -> block, "blockingInfo" -> grater[BlockingInfo].asDBObject(BlockingInfo(whoBlocks))), false, true)

    // update all embedded links via the from and mark them as blocked / unblocked - for both ends of the links, respectively
    val (fromThings, fromMDRs) = Link.find(MongoDBObject("from.id" -> id, "from.hubType" -> objectType)).partition(_.from.hubType != Some(MDR))
    val (toThings, toMDRs) = Link.find(MongoDBObject("to.id" -> id, "to.hubType" -> objectType)).partition(_.from.hubType != Some(MDR))

    val things = (fromThings ++ toThings).toList
    val mdrs = (fromMDRs ++ toMDRs).toList

    val groupedFromThings = makeUGCLinkGroup(things, _.from)
    updateUGCEmbeddedLinks(groupedFromThings)

    val groupedToThings = makeUGCLinkGroup(things, _.to)
    updateUGCEmbeddedLinks(groupedToThings)

    val groupedFromMDRs = makeMDRLinkGroup(mdrs, _.from)
    updateMDREmbeddedLinks(groupedFromMDRs)

    val groupedToMDRs = makeMDRLinkGroup(mdrs, _.to)
    updateMDREmbeddedLinks(groupedToMDRs)

  }


  def findTo(toUri: String, linkType: String) = Link.find(MongoDBObject("linkType" -> linkType, "to.uri" -> toUri)).toList


  // ~~~ shared link creation, we maybe find a better place for this

  def createThumbnailLink(fromId: ObjectId, fromType: String, hubId: String, userName: String, hostName: String) = {
    val Array(orgId, spec, recordId) = hubId.split("_")
    val mdrCollectionName = DataSet.getRecordsCollectionName(orgId, spec)

    val fromCollection = fromType match {
      case USERCOLLECTION => userCollectionsCollection
      case STORY => userStoriesCollection
      case _ => throw new ProgrammerException("What are you doing?")
    }

    Link.create(
      linkType = Link.LinkType.THUMBNAIL,
      userName = userName,
      value = Map.empty,
      from = LinkReference(
        uri = Some(Link.buildUri(fromType, fromId.toString, hostName)),
        id = Some(fromId),
        hubType = Some(fromType)
      ),
      to = LinkReference(
        uri = Some(Link.buildUri(MDR, hubId, hostName)),
        hubType = Some(MDR),
        hubCollection = Some(mdrCollectionName),
        hubAlternativeId = Some(hubId)
      ),
      embedFrom = Some(EmbeddedLinkWriter(
        value = Some(Map(MDR_HUB_ID -> hubId)),
        collection = fromCollection,
        id = Some(fromId)
      ))
    )

  }

}

/**
 * An arrow in a link. This is flexible: if we have a hubType we can lookup by mongo id, or else we have a uri.
 */
case class LinkReference(id: Option[ObjectId] = None, // mongo id for hub-based reference
                         hubType: Option[String] = None, // internal CH type of the reference (DObject, ...)
                         hubCollection: Option[String] = None, // name of the mongodb collection this reference lives in, if it can't be infered from the hubType
                         hubAlternativeId: Option[String] = None, // alternative ID value in case ID does not apply
                         uri: Option[String] = None, // URI
                         refType: Option[String] = None)

// external type of the reference


/**
 * A denormalized bit of a link that lives in a linked object. Makes it easy to do lookups.
 */
case class EmbeddedLink(TS: Date = new Date(),
                        userName: String,
                        linkType: String,
                        link: ObjectId,
                        value: Map[String, String] = Map.empty[String, String],
                        blocked: Boolean = false)

/**
 * This guy knows how to write an embedded link and give it a value
 */
case class EmbeddedLinkWriter(value: Option[Map[String, String]] = None, collection: MongoCollection, id: Option[ObjectId] = None, alternativeId: Option[(String, String)] = None) {

  def write(embeddedLink: EmbeddedLink): Either[String, String] = {
    if (id == None && alternativeId == None) return Left("No ID provided for writing embedded link")
    val el = value match {
      case Some(v) => embeddedLink.copy(value = v)
      case None => embeddedLink
    }
    val serEmb = grater[EmbeddedLink].asDBObject(el)
    id match {
      case Some(objectId) =>
        collection.update(MongoDBObject("_id" -> objectId), $push("links" -> serEmb))
        // TODO check write result
        Right("ok")
      case None =>
        alternativeId match {
          case Some(tuple) =>
            val field = tuple._1
            val value = tuple._2
            collection.update(MongoDBObject(field -> value), $push("links" -> serEmb))
            // TODO check write result
            Right("ok")
          case None => Left("impossible!")
        }
    }
  }
}