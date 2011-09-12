package models

import com.novus.salat.dao.SalatDAO
import models.salatContext._
import org.bson.types.ObjectId
import com.novus.salat.EnumStrategy
import com.novus.salat.annotations.raw.EnumAs
import org.joda.time.DateTime

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Story(_id: ObjectId = new ObjectId,
                 TS_update: DateTime,
                 name: String,
                 description: String,
                 user_id: ObjectId,
                 userName: String,
                 visibility: String, // Visibility.Value
                 isDraft: Boolean,
                 pages: List[Page]) {
}

object Story extends SalatDAO[Story, ObjectId](userStoriesCollection) with Commons[Story] with Resolver[Story] with Pager[Story]

@EnumAs(strategy = EnumStrategy.BY_VALUE)
object Visibility extends Enumeration {
  val Private, Public = Value
}

case class Page(title: String, text: String, objects: List[PageObject], thumbnail: Option[ObjectId])

/** object in a page. may contain more things such as position, location, ... **/
case class PageObject(dobject_id: ObjectId)