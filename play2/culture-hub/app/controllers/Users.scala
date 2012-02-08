package controllers

import play.api.mvc.Action
import com.mongodb.casbah.Imports._
import models.User
import java.util.regex.Pattern

/**
 * todo: javadoc
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */


object Users extends DelvingController {

  def list(query: String, page:Int) = Root {
    Action {
      implicit request =>
      // ~~~ temporary hand-crafted search for users
        import views.Helpers.PAGE_SIZE
        def queryOk(query: String) = query != null && query.trim().length > 0
        val queriedUsers = (if (queryOk(query)) User.find(MongoDBObject("firstName" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE))) ++ User.find(MongoDBObject("lastName" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE))) else User.findAll).toList
        val pageEndIndex: Int = (page + 1) * PAGE_SIZE
        val listMax = queriedUsers.length
        val pageEnd = if (listMax < pageEndIndex) listMax else pageEndIndex
        val usersPage = queriedUsers.slice((page - 1) * PAGE_SIZE, pageEnd)

        val items: List[ListItem] = usersPage.toList
        Ok(Template("/user/list.html", 'title -> listPageTitle("user"), 'items -> items, 'page -> page, 'count -> queriedUsers.length))
    }
  }

  def listAsTokens(orgId: String, q: String) = Root {
    Action {
      implicit request =>
        val query = MongoDBObject("isActive" -> true, "userName" -> Pattern.compile(q, Pattern.CASE_INSENSITIVE))
        val users = if(orgId != null) User.find(query ++ MongoDBObject("organizations" -> orgId)) else User.find(query)
        val asTokens = users.map(u => Token(u.userName, u.userName))
        Json(asTokens)
    }
  }

}