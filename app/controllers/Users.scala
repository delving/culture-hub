package controllers

import models.User
import com.mongodb.DBObject
import play.mvc.results.Result
import org.bson.types.ObjectId
import play.templates.Html
import com.mongodb.casbah.commons.MongoDBObject
import java.util.regex.Pattern

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Users extends DelvingController {

  import views.User._

  def index(user: String): AnyRef = {
    val u = getUser(user) match {
      case Right(aUser) => aUser
      case Left(error) => return error
    }
    html.index(username = u.reference.username)
  }

  def list(query: String, page: Int = 1): Html = {

    // ~~~ temporary hand-crafted search for users
    import views.context.PAGE_SIZE
    def queryOk(query: String) = query != null && query.trim().length > 0
    val queriedUsers = (if(queryOk(query)) User.find(MongoDBObject("firstName" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE))) ++ User.find(MongoDBObject("lastName" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE))) else User.findAll).toList
    val pageEndIndex: Int = (page + 1) * PAGE_SIZE
    val listMax = queriedUsers.length
    val pageEnd = if (listMax < pageEndIndex) listMax else pageEndIndex
    val usersPage = queriedUsers.slice((page - 1) * PAGE_SIZE, pageEnd)

    views.html.list(title = listPageTitle("user"), itemName = "user", items = usersPage.toList, page = page, count = queriedUsers.length)

  }

  def listAsTokens(q: String): Result = {
    // TODO this could rather be a mongo query
    val userTokens: List[Token] = for(u: DBObject <- User.findAllIdName.filter(user => (user.get("firstName") + " " + user.get("lastName")) contains (q))) yield {
      Token(id = u.get("reference").asInstanceOf[DBObject].get("id").toString, name = u.get("firstName") + " " + u.get("lastName"))
    }
    Json(userTokens)
  }

}