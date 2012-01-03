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

package controllers.user

import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import _root_.util.Constants._
import models.salatContext._
import com.novus.salat.grater
import controllers.{ShortObjectModel, DelvingController}
import models._
import views.context.DEFAULT_THUMBNAIL
import util.ProgrammerException

/**
 * Common operations amongst UGC controllers.
 *
 * TODO move this where it actually belongs to
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait UGCController { self: DelvingController =>

  def getThumbnailId(collectionId: ObjectId, thumbnailId: String, thumbnailUrl: Option[String]) = thumbnailId match {
    case oid if ObjectId.isValid(oid) => Right(new ObjectId(oid))
    case hubId if hubId.count(_ == '_') == 2 => Left(thumbnailUrl.getOrElse(DEFAULT_THUMBNAIL))
    case _ => Left(DEFAULT_THUMBNAIL)
  }

  def setThumbnaiL(fromId: ObjectId, fromType: String, thumbnailId: String, thumbnailUrl: Option[String], thumbnailLink: Option[ObjectId]) {
    // remove existing thumbnail link, if any
    thumbnailLink.foreach(Link.removeById(_))

    if(thumbnailId.length() == 0) {
      return
    }

    val collection = fromType match {
      case USERCOLLECTION => userCollectionsCollection
      case STORY => userStoriesCollection
      case _ => throw new ProgrammerException("Wrong fromType for thumbnail")
    }

    getThumbnailId(fromId, thumbnailId, thumbnailUrl) match {
      case Right(oid: ObjectId) =>
        collection.update(MongoDBObject("_id" -> fromId), $set ("thumbnail_id" -> oid) ++ $unset("thumbnail_url"))
      case Left(url: String) =>
        Link.createThumbnailLink(fromId, fromType, thumbnailId, connectedUser, request.domain)
        collection.update(MongoDBObject("_id" -> fromId), $set("thumbnail_url" -> url) ++ $unset("thumbnail_id"))
    }
  }

  def retrieveMDRs(links: List[Link]): List[ShortObjectModel] = {
    val mdrIds = links.filter(_.from.hubType == Some(MDR)).map(l => (l.from.hubCollection.get, l.from.hubAlternativeId.get))
    val mdrs = mdrIds.groupBy(_._1).map(m => connection(m._1).find(MDR_HUB_ID $in m._2.map(_._2)).toList).flatten.map(grater[MetadataRecord].asObject(_))
    mdrs.flatMap(mdr =>
      // we assume that the first mapping we find will do
      if(!mdr.mappedMetadata.isEmpty) {
        val Array(orgId, spec, localRecordKey) = mdr.hubId.split("_")
        DataSet.findBySpecAndOrgId(spec, orgId) match {
          case Some(ds) =>
            val record = mdr.getAccessor(ds.getIndexingMappingPrefix.getOrElse(""))
            Some(record)
          case None =>
            warning("Could not find DataSet for " + mdr.hubId)
            None // huh?!?
        }
      } else {
        None
      }).toList
  }

}