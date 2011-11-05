package models

import com.novus.salat.dao.SalatDAO
import models.salatContext._
import org.bson.types.ObjectId
import java.util.Date

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
                 thumbnail_id: Option[ObjectId],
                 isDraft: Boolean,
                 pages: List[Page]) extends Thing

object Story extends SalatDAO[Story, ObjectId](userStoriesCollection) with Commons[Story] with Resolver[Story] with Pager[Story] {

  def fetchName(id: String): String = findById(id).get.name

}

case class Page(title: String, text: String, objects: List[PageObject])

/** object in a page. may contain more things such as position, location, ... **/
case class PageObject(dobject_id: ObjectId)