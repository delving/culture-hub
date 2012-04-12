package controllers

import play.api.mvc._
import models._
import core.ThemeInfo
import com.mongodb.casbah.Imports._
import util.Constants._

object Application extends DelvingController {


  def index = Root {
    Action {
      implicit request =>
        val themeInfo = renderArgs("themeInfo").get.asInstanceOf[ThemeInfo]
        val recentCollections: List[ListItem] = UserCollection.findRecent(themeInfo.themeProperty("recentCollectionsCount", classOf[Int])).toList
        val recentStories: List[Story] = Story.findRecent(themeInfo.themeProperty("recentStoriesCount", classOf[Int])).toList
        val recentObjects: List[ListItem] = DObject.findRecent(themeInfo.themeProperty("recentObjectsCount", classOf[Int])).toList
        val recentMdrs: List[ListItem] = Search.search(None, 1, theme, List("%s:%s AND %s:%s".format(RECORD_TYPE, MDR, HAS_DIGITAL_OBJECT, true)))._1.slice(0, themeInfo.themeProperty("recentMdrsCount", classOf[Int]))
        val homepageCmsContent =  CMSPage.find(MongoDBObject("key" -> "homepage", "lang" -> getLang, "theme" -> theme.name)).$orderby(MongoDBObject("_id" -> -1)).limit(1).toList.headOption

        homepageCmsContent match {
        case None =>
          Ok(Template('recentCollections -> recentCollections, 'recentStories -> recentStories, 'recentObjects -> recentObjects, 'recentMdrs -> recentMdrs))
        case Some(cmsContent) =>
          Ok(Template('recentCollections -> recentCollections, 'recentStories -> recentStories, 'recentObjects -> recentObjects, 'recentMdrs -> recentMdrs, 'homepageCmsContent -> cmsContent))
        }
    }
  }

  def page(key: String) = Root {
    Action {
      implicit request =>
        // TODO link the themes to the organization so this also works on multi-org hubs
        CMSPage.find(MongoDBObject("key" -> key, "lang" -> getLang, "theme" -> theme.name)).$orderby(MongoDBObject("_id" -> -1)).limit(1).toList.headOption match {
          case None => NotFound(key)
          case Some(page) => Ok(Template('page -> page))
        }
    }
  }

  def notFound(what: String) = Action {
    implicit request => Results.NotFound(what)
  }


}