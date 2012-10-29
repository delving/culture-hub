package controllers

import play.api.mvc.Action
import models.cms.CMSPage
import com.mongodb.casbah.Imports._
import scala.Some

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object CMS extends DelvingController {

  def page(key: String) = Root {
    Action {
      implicit request =>
        CMSPage.dao.find(MongoDBObject("key" -> key, "lang" -> getLang, "orgId" -> configuration.orgId)).$orderby(MongoDBObject("_id" -> -1)).limit(1).toList.headOption match {
          case None => NotFound(key)
          case Some(page) => Ok(Template('page -> page))
        }
    }
  }

}
