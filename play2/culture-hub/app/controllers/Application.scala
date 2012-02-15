package controllers

import play.api.mvc._
import models._
import core.ThemeInfo
import com.mongodb.casbah.Imports._

object Application extends DelvingController {

  def index = Root {
    Action {
      implicit request =>
        val themeInfo = renderArgs("themeInfo").get.asInstanceOf[ThemeInfo]
        val recentCollections: List[ListItem] = UserCollection.findRecent(themeInfo.themeProperty("recentCollectionsCount", classOf[Int])).toList
        val recentStories: List[ListItem] = Story.findRecent(themeInfo.themeProperty("recentStoriesCount", classOf[Int])).toList
        val recentObjects: List[ListItem] = DObject.findRecent(themeInfo.themeProperty("recentObjectsCount", classOf[Int])).toList
        // TODO MIGRATION
        //    val recentMdrs: List[ListItem] = Search.search(None, request, theme, List("%s:%s AND %s:%s".format(RECORD_TYPE, MDR, HAS_DIGITAL_OBJECT, true)))._1.slice(0, viewUtils.themeProperty("recentMdrsCount", classOf[Int]))
        val recentMdrs = List.empty[ListItem]

        Ok(Template('recentCollections -> recentCollections, 'recentStories -> recentStories, 'recentObjects -> recentObjects, 'recentMdrs -> recentMdrs))
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


}