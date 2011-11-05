package controllers

import models.User
import play.mvc.results.Result
import com.mongodb.casbah.commons.MongoDBObject
import java.util.regex.Pattern

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Users extends DelvingController {

  def index(user: String): AnyRef = {
    val u = getUser(user) match {
      case Right(aUser) => aUser
      case Left(error) => return error
    }
    Template
  }

  def list(query: String, page: Int = 1): Result = {

    // ~~~ temporary hand-crafted search for users
    import views.context.PAGE_SIZE
    def queryOk(query: String) = query != null && query.trim().length > 0
    val queriedUsers = (if(queryOk(query)) User.find(MongoDBObject("firstName" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE))) ++ User.find(MongoDBObject("lastName" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE))) else User.findAll).toList
    val pageEndIndex: Int = (page + 1) * PAGE_SIZE
    val listMax = queriedUsers.length
    val pageEnd = if (listMax < pageEndIndex) listMax else pageEndIndex
    val usersPage = queriedUsers.slice((page - 1) * PAGE_SIZE, pageEnd)

    val items: List[ListItem] = usersPage.toList
    Template("/user/list.html", 'title -> listPageTitle("user"), 'items -> items, 'page -> page, 'count -> queriedUsers.length)
  }

}