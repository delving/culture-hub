package controllers

import play.mvc.results.Result
import models.UserCollection

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Collections extends DelvingController {

  import views.Collection._
  
  def list(user: String): AnyRef = {
    val userObject = getUser(user)
    html.list(user = userObject)
  }

  def collection(user: String, collection: String): AnyRef = {
    val u = getUser(user)
    html.collection(user = u, name = collection)
  }

  def add(user: String): AnyRef = {
    val u = getUser(user)
    html.add(user = u)
  }

  /** list all user collections the connected user can write to as tokens **/
  def listWriteableAsTokens: Result = {
    val userCollections = for(userCollection <- UserCollection.findAllWriteable(getUserReference)) yield Token(userCollection._id.toString, userCollection.name)
    Json(userCollections)
  }


}