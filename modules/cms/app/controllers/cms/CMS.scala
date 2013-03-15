package controllers.cms

import play.api.mvc.{ AnyContent, Action }
import models.cms.{ MenuEntry, CMSPage }
import com.mongodb.casbah.Imports._
import core.MenuElement
import scala.collection.JavaConverters._
import controllers.DelvingController

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object CMS extends DelvingController {

  def page(key: String, menuKey: Option[String]): Action[AnyContent] = Root {
    Action {
      implicit request =>
        CMSPage.dao.find(
          MongoDBObject("key" -> key, "lang" -> getLang, "orgId" -> configuration.orgId)
        ).$orderby(MongoDBObject("_id" -> -1)).limit(1).toList.headOption match {
            case None => NotFound(key)
            case Some(page) =>
              if (menuKey.isDefined) {
                val subMenu = MenuEntry.dao.findEntries(configuration.orgId, menuKey.get).map { e =>
                  MenuElement(
                    url = "/site/" + menuKey.get + "/page/" + e.targetPageKey.getOrElse(""),
                    titleKey = e.title.get(getLang).getOrElse(e.menuKey)
                  )
                }.toSeq.asJava
                Ok(Template('page -> page, 'menuEntries -> subMenu, 'menuKey -> menuKey.get))
              } else {
                Ok(Template('page -> page))
              }
          }
    }
  }

}
