package controllers

import play.templates.Html
import models.{DObject, Story, UserCollection}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Application extends DelvingController {

  import views.Application._

  def index: Html = {

    val recentCollections: List[ShortCollection] = UserCollection.findRecent(3).toList
    val recentStories: List[ShortStory] = Story.findRecent(3).toList
    val recentObjects: List[ListItem] = DObject.findRecent(12).toList

    html.index(recentCollections, recentStories, recentObjects)

  }

}