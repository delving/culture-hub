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
import salatContext._
import java.util.Date
import com.mongodb.casbah.Imports._
import util.Constants._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class UserCollection(_id: ObjectId = new ObjectId,
                          TS_update: Date,
                          user_id: ObjectId,
                          userName: String,
                          name: String,
                          description: String,
                          visibility: Visibility,
                          deleted: Boolean = false,
                          thumbnail_id: Option[ObjectId],
                          thumbnail_url: Option[String],
                          links: List[EmbeddedLink] = List.empty[EmbeddedLink],
                          isBookmarksCollection: Option[Boolean] = None) extends Thing {

  def getType = USERCOLLECTION

  // meh...
  def getBookmarksCollection = isBookmarksCollection.getOrElse(false)
}

object UserCollection extends SalatDAO[UserCollection, ObjectId](userCollectionsCollection) with Commons[UserCollection] with Resolver[UserCollection] with Pager[UserCollection] {

  def fetchName(id: String): String = fetchName(id, userCollectionsCollection)

  def setObjects(id: ObjectId, objectIds: List[ObjectId]) {
    userCollectionsCollection.update(MongoDBObject("_id" -> id), $set("linkedObjects" -> objectIds))
  }

  def createThumbnailLink(userCollection: ObjectId, hubId: String, userName: String) = {
    val Array(orgId, spec, recordId) = hubId.split("_")
    val mdrCollectionName = DataSet.getRecordsCollectionName(orgId, spec)

    Link.create(
      linkType = Link.LinkType.THUMBNAIL,
      userName = userName,
      value = Map.empty,
      from = LinkReference(
        id = Some(userCollection),
        hubType = Some(USERCOLLECTION)
      ),
      to = LinkReference(
        hubType = Some(MDR),
        hubCollection = Some(mdrCollectionName),
        hubAlternativeId = Some(hubId)
      ),
      embedFrom = Some(EmbeddedLinkWriter(
        value = Some(Map(MDR_HUB_ID -> hubId)),
        collection = userCollectionsCollection,
        id = Some(userCollection)
      ))
    )

  }

}