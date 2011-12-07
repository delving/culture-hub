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
import models.salatContext._
import org.bson.types.ObjectId
import java.util.Date
import util.Constants._

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
                 thumbnail_id: Option[ObjectId],
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

  def fetchName(id: String): String = fetchName(id, userStoriesCollection)

}

case class Page(title: String, text: String, objects: List[PageObject])

/**object in a page. may contain more things such as position, location, ... **/
case class PageObject(dobject_id: ObjectId)