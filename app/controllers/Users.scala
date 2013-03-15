package controllers

import play.api.mvc.Action
import com.mongodb.casbah.Imports._
import models.HubUser
import java.util.regex.Pattern

/**
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

object Users extends DelvingController {

  def list(query: String, page: Int) = Root {
    Action {
      implicit request =>

        // ~~~ temporary hand-crafted search for users
        def queryOk(query: String) = query != null && query.trim().length > 0
        val queriedUsers: List[HubUser] = if (queryOk(query)) {
          (
            HubUser.dao.find(MongoDBObject("firstName" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE))) ++
            HubUser.dao.find(MongoDBObject("lastName" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE)))
          ).toList
        } else {
          HubUser.dao.findAll
        }.toList

        val pageEndIndex: Int = (page + 1) * configuration.searchService.pageSize
        val listMax = queriedUsers.length
        val pageEnd = if (listMax < pageEndIndex) listMax else pageEndIndex
        val usersPage = queriedUsers.slice((page - 1) * configuration.searchService.pageSize, pageEnd)

        val items: List[HubUser] = usersPage.toList
        Ok(Template("/user/list.html", 'title -> listPageTitle("user"), 'items -> items, 'page -> page, 'count -> queriedUsers.length))
    }
  }

  def listAsTokens(orgId: Option[String], q: String) = Root {
    Action {
      implicit request =>
        def queryBy(field: String): MongoDBObject = MongoDBObject(field -> Pattern.compile(q, Pattern.CASE_INSENSITIVE))
        val queryByUserName = queryBy("userName")
        val queryByFirstName = queryBy("firstName")
        val queryByLastName = queryBy("lastName")
        lazy val orgQuery: MongoDBObject = MongoDBObject("organizations" -> orgId.get)

        val baseQuery = Seq(queryByUserName, queryByFirstName, queryByLastName)
        val query: Seq[MongoDBObject] = if (orgId != None) baseQuery ++ Seq(orgQuery) else baseQuery

        val users: Seq[HubUser] = query.flatMap { HubUser.dao.find(_).toSeq }.distinct
        val asTokens = users.map(u => Token(u.userName, u.userName + " - " + u.fullname))

        Json(asTokens)
    }
  }

}