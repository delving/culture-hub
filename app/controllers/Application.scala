package controllers

import models.{DObject, Story, UserCollection}
import play.mvc.results.Result

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Application extends DelvingController {

  def index: Result = {

    val recentCollections: List[ListItem] = UserCollection.findRecent(viewUtils.themeProperty("recentCollectionsCount", classOf[Int])).toList
    val recentStories: List[ListItem] = Story.findRecent(viewUtils.themeProperty("recentStoriesCount", classOf[Int])).toList
    val recentObjects: List[ListItem] = DObject.findRecent(viewUtils.themeProperty("recentObjectsCount", classOf[Int])).toList

    Template('recentCollections -> recentCollections, 'recentStories -> recentStories, 'recentObjects -> recentObjects)
  }

}