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
import com.novus.salat.dao.SalatDAO
import core.Constants._
import mongoContext._
import java.util.Date
import com.mongodb.casbah.Imports._
import core.indexing.IndexingService

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class UserCollection(_id: ObjectId = new ObjectId,
                          TS_update: Date,
                          userName: String,
                          name: String,
                          description: String,
                          visibility: Visibility,
                          deleted: Boolean = false,
                          blocked: Boolean = false,
                          blockingInfo: Option[BlockingInfo] = None,
                          thumbnail_id: Option[ObjectId],
                          thumbnail_url: Option[String],
                          links: List[EmbeddedLink] = List.empty[EmbeddedLink],
                          isBookmarksCollection: Option[Boolean] = None) extends Thing {

  def getType = USERCOLLECTION

  // meh...
  def getBookmarksCollection = isBookmarksCollection.getOrElse(false)

  def getLinkedMDRAccessors: List[MetadataAccessors] = {
    val mdrLinks = links.filter(el => el.linkType == Link.LinkType.PARTOF && el.value.contains(MDR_HUB_ID)).groupBy(_.value(MDR_HUBCOLLECTION)).map(grouped => (grouped._1, grouped._2.map(_.value(MDR_HUB_ID)))).toMap
    mdrLinks.map(l => MetadataRecord.getAccessors(l._1, l._2 : _ *)).toList.flatten
  }

}

object UserCollection extends SalatDAO[UserCollection, ObjectId](userCollectionsCollection) with Commons[UserCollection] with Resolver[UserCollection] with Pager[UserCollection] {

  def block(id: ObjectId, whoBlocks: String) {
    UserCollection.findOneByID(id) map {
      c =>
        val updated = c.copy(blocked = true, blockingInfo = Some(BlockingInfo(whoBlocks)))
        UserCollection.save(updated)
        Link.blockLinks(USERCOLLECTION, c._id, whoBlocks)
        IndexingService.deleteById(c._id)
    }
  }

  def fetchName(id: String): String = fetchName(id, userCollectionsCollection)

  def setObjects(id: ObjectId, objectIds: List[ObjectId]) {
    userCollectionsCollection.update(MongoDBObject("_id" -> id), $set("linkedObjects" -> objectIds))
  }

}