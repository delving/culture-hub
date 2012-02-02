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

import org.bson.types.ObjectId
import mongoContext._
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO
import controllers.dos.StoredFile
import java.util.Date
import util.Constants._
import views.Helpers.DEFAULT_THUMBNAIL
import Commons.FilteredMDO
import core.indexing.IndexingService

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DObject(_id: ObjectId = new ObjectId,
                   TS_update: Date,
                   user_id: ObjectId,
                   userName: String,
                   name: String,
                   description: String,
                   visibility: Visibility,
                   deleted: Boolean = false,
                   blocked: Boolean = false,
                   blockingInfo: Option[BlockingInfo] = None,
                   thumbnail_id: Option[ObjectId],
                   thumbnail_url: Option[String] = None,
                   links: List[EmbeddedLink] = List.empty[EmbeddedLink],
                   thumbnail_file_id: Option[ObjectId] = None, // pointer to the file selected as the thumbnail. This is _not_ helping to fetch the thumbnail, which is retrieved using the ID of the object
                   files: Seq[StoredFile] = Seq.empty[StoredFile]) extends Thing {

  def getType = OBJECT

  def getSourceFileUrl = if(thumbnail_file_id.isDefined) "/file/" + thumbnail_file_id.get else DEFAULT_THUMBNAIL

  override def toSolrDocument = {
    val doc = getAsSolrDocument
    doc addField (COLLECTIONS, links.filter(l => l.linkType == Link.LinkType.PARTOF && !l.blocked).map(_.value(USERCOLLECTION_ID)).toArray)
    doc
  }
  def fileIds = files.map(_.id)

  def linkedCollections: List[UserCollection] = UserCollection.find(FilteredMDO("links.value.objectId" -> _id.toString)).toList

  def linkedStories: List[Story] = Story.find(FilteredMDO("pages.objects.objectId" -> _id)).toList

  override def getMimeType = files.headOption match {
    case Some(o) => o.contentType
    case None => super.getMimeType
  }
}

object DObject extends SalatDAO[DObject, ObjectId](objectsCollection) with Commons[DObject] with Resolver[DObject] with Pager[DObject] {

  def block(id: ObjectId, whoBlocks: String) {

    DObject.findOneByID(id) map {
      o =>
        val updated = o.copy(blocked = true, blockingInfo = Some(BlockingInfo(whoBlocks)))
        DObject.save(updated)
        Link.blockLinks(OBJECT, o._id, whoBlocks)
        IndexingService.deleteById(o._id)
    }

  }

  def findAllWithCollection(id: ObjectId) = find(FilteredMDO("links.value.%s".format(USERCOLLECTION_ID) -> id.toString, "links.linkType" -> Link.LinkType.PARTOF, "links.blocked" -> false)).toList

  def findAllUnassignedForUser(userName: String) = find(FilteredMDO("userName" -> userName) ++ ("links.linkType" $ne (Link.LinkType.PARTOF))).toList

  def updateThumbnail(id: ObjectId, thumbnail_id: ObjectId) {
    update(MongoDBObject("_id" -> id), $set ("thumbnail_file_id" -> thumbnail_id, "thumbnail_id" -> id) , false, false)
  }

  def fetchName(id: String): String = fetchName(id, objectsCollection)

  def removeFile(oid: ObjectId) {
    DObject.update(MongoDBObject(), (MongoDBObject("$pull" -> MongoDBObject("files" -> MongoDBObject("id" -> oid)))))
  }

  def unlinkCollection(collectionId: ObjectId) {
    DObject.update(MongoDBObject("collections" -> collectionId), $pull ("collections" -> collectionId))
  }

}