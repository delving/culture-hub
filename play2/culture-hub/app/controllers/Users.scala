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

  //  def index(user :String) = Root{
  //    Action {
  //      implicit request =>
  //      val u = getUser(user) match {
  //        case Right(aUser) => aUser
  //        case Left(error) => return error
  //      }
  //      Template
  //    }
  //  }


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
}