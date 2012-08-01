package controllers

import play.api.mvc._
import models._
import cms.CMSPage
import com.mongodb.casbah.Imports._
import core.ThemeInfo
import core.Constants._

object Application extends DelvingController {


  def index = Root {
    Action {
      implicit request =>
        val themeInfo = renderArgs("themeInfo").get.asInstanceOf[ThemeInfo]
        val recentMdrs: List[ListItem] = try {
          Search.search(None, 1, configuration, List("%s:%s AND %s:%s".format(RECORD_TYPE, MDR, HAS_DIGITAL_OBJECT, true)))._1.slice(0, themeInfo.themeProperty("recentMdrsCount", classOf[Int]))
        } catch {
          case t =>
            List.empty
        }
        val homepageCmsContent = CMSPage.dao.find(MongoDBObject("key" -> "homepage", "lang" -> getLang, "theme" -> configuration.name)).$orderby(MongoDBObject("_id" -> -1)).limit(1).toList.headOption

        homepageCmsContent match {
          case None =>
            Ok(Template('recentMdrs -> recentMdrs))
          case Some(cmsContent) =>
            Ok(Template('recentMdrs -> recentMdrs, 'homepageCmsContent -> cmsContent))
        }
    }
  }

  def page(key: String) = Root {
    Action {
      implicit request =>
      // TODO link the themes to the organization so this also works on multi-org hubs
        CMSPage.dao.find(MongoDBObject("key" -> key, "lang" -> getLang, "theme" -> configuration.name)).$orderby(MongoDBObject("_id" -> -1)).limit(1).toList.headOption match {
          case None => NotFound(key)
          case Some(page) => Ok(Template('page -> page))
        }
    }
  }

  def notFound(what: String) = Action {
    implicit request => Results.NotFound(what)
  }


}