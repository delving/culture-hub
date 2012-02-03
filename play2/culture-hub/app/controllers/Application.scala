package controllers

import play.api.mvc._
import models.{DObject, Story, UserCollection}
import core.ThemeInfo

object Application extends ApplicationController with ModelImplicits {

  def index = Themed {
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

}