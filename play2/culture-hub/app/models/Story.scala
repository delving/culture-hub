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

import org.apache.solr.common.SolrInputDocument
import com.novus.salat.dao.SalatDAO
import mongoContext._
import org.bson.types.ObjectId
import java.util.Date
import util.Constants._
import core.indexing.IndexingService
import controllers.{ModelImplicits, ShortObjectModel}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Story(_id: ObjectId = new ObjectId,
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
                 thumbnail_url: Option[String],
                 isDraft: Boolean,
                 pages: List[Page],
                 links: List[EmbeddedLink] = List.empty[EmbeddedLink]) extends Thing {

  def getType = STORY

  override def toSolrDocument: SolrInputDocument = {
    val doc = getAsSolrDocument
    pages foreach {
      page => {
        val pageNumber = pages.indexOf(page)
        doc addField("delving_page_title_%d_text".format(pageNumber), page.title)
        doc addField("delving_page_text_%d_text".format(pageNumber), page.text)
      }
    }
    doc
  }
}

object Story extends SalatDAO[Story, ObjectId](userStoriesCollection) with Commons[Story] with Resolver[Story] with Pager[Story] {

  def block(id: ObjectId, whoBlocks: String) {
    Story.findOneByID(id) map {
      s =>
        val updated = s.copy(blocked = true, blockingInfo = Some(BlockingInfo(whoBlocks)))
        Story.save(updated)
        Link.blockLinks(STORY, s._id, whoBlocks)
        IndexingService.deleteById(s._id)
    }
  }

  def fetchName(id: String): String = fetchName(id, userStoriesCollection)

}

case class Page(title: String, text: String, objects: List[PageObject]) extends ModelImplicits {

  def getPageObjects = {
    val (userPageObjects, mdrPageObjects) = objects.partition(_.objectId != None)
    val userObjects: List[ShortObjectModel] = DObject.findAllWithIds(userPageObjects.flatMap(_.objectId)).toList
    val groupedIds = mdrPageObjects.flatMap(_.hubId).groupBy(id => {
      val Array(orgId, spec, localRecordKey) = id.split("_")
      (orgId, spec)
    })
    val heritageObjects: List[ShortObjectModel] = groupedIds.map(g => MetadataRecord.getAccessors(g._1, g._2 : _ *)).toList.flatten
    userObjects ++ heritageObjects
  }

}

/**
 * References an object in a page, either a UserObject or MDR. May contain additional information such as position in the page etc.
 */
case class PageObject(objectId: Option[ObjectId] = None, hubId: Option[String] = None)