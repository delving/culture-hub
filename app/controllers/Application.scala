package controllers

import models.{DObject, Story, UserCollection}
import play.mvc.results.Result

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Application extends DelvingController {

  def index: Result = {

    val recentCollections: List[ShortCollection] = UserCollection.findRecent(3).toList
    val recentStories: List[ShortStory] = Story.findRecent(3).toList
    val recentObjects: List[ListItem] = DObject.findRecent(12).toList

    Template('recentCollections -> recentCollections, 'recentStories -> recentStories, 'recentObjects -> recentObjects)
  }

}