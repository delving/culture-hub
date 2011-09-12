package controllers

import models.User
import com.mongodb.DBObject
import play.mvc.results.Result
import org.bson.types.ObjectId
import play.templates.Html

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

  def list(query: String, page: Int = 1): Html = {
    val usersPage = User.findAll.page(page)

    html.list(users = usersPage._1, page = page, count = usersPage._2)

  }

  def listAsTokens(q: String): Result = {
    // TODO this could rather be a mongo query
    val userTokens: List[Token] = for(u: DBObject <- User.findAllIdName.filter(user => (user.get("firstName") + " " + user.get("lastName")) contains (q))) yield {
      Token(id = u.get("reference").asInstanceOf[DBObject].get("id").toString, name = u.get("firstName") + " " + u.get("lastName"))
    }
    Json(userTokens)
  }

}

case class ShortUser(id: ObjectId, name: String, userName: String)