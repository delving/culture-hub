package controllers

import models.User
import com.mongodb.DBObject
import play.mvc.results.Result

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Users extends DelvingController {

  import views.User._

  def index(user: String) = {
    val u = getUser(user)
    html.index(username = u.reference.username)
  }

  def listAsTokens(q: String): Result = {
    // TODO this could rather be a mongo query
    val userTokens: List[Token] = for(u: DBObject <- User.findAllIdName.filter(user => (user.get("firstName") + " " + user.get("lastName")) contains (q))) yield {
      Token(id = u.get("reference").asInstanceOf[DBObject].get("id").toString, name = u.get("firstName") + " " + u.get("lastName"))
    }
    Json(userTokens)
  }

}