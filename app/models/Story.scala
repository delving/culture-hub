package models

import com.novus.salat.dao.SalatDAO
import models.salatContext._
import org.bson.types.ObjectId
import com.novus.salat.EnumStrategy
import com.novus.salat.annotations.raw.EnumAs

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Story(_id: ObjectId = new ObjectId,
                 name: String,
                 description: String,
                 user_id: ObjectId,
                 visibility: Status.Value,
                 pages: List[Page])

object Story extends SalatDAO[Story, ObjectId](userStoriesCollection) with Resolver[Story]

@EnumAs(strategy = EnumStrategy.BY_VALUE)
object Status extends Enumeration {
  val Private, Public = Value
}

case class Page(title: String, text: String, objects: List[PageObject])

/** object in a page. may contain more things such as position, location, ... **/
case class PageObject(dobject_id: ObjectId)