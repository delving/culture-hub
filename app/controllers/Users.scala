/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import models.User
import play.mvc.results.Result
import com.mongodb.casbah.Imports._
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

  def listAsTokens(q: String, orgId: String): Result = {
    val query = MongoDBObject("isActive" -> true, "userName" -> Pattern.compile(q, Pattern.CASE_INSENSITIVE))
    val users = if(orgId != null) User.find(query ++ MongoDBObject("organizations" -> orgId)) else User.find(query)
    val asTokens = users.map(u => Token(u.userName, u.userName, "user"))
    Json(asTokens)
  }

}