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

import play.mvc.Scope.Session
import java.util.Date
import models.{UserCollection, Visibility, User}
import components.IndexingService

class ServicesSecurity extends Security with Internationalization {

  def authenticate(username: String, password: String): Boolean = {
    User.connect(username, password)
  }

  def onAuthenticated(userName: String, session: Session) {
    val user = User.findByUsername(userName)
    if(user == None) {
      throw new RuntimeException(&("servicessecurity.userNotFound", userName))
    }

    User.findBookmarksCollection(userName) match {
      case None =>
        // create default bookmarks collection
        val bookmarksCollection = UserCollection(
          TS_update = new Date(),
          user_id = user.get._id,
          userName = userName,
          name = "Bookmarks",
          description = "Bookmarks",
          visibility = Visibility.PRIVATE,
          thumbnail_id = None,
          thumbnail_url = None,
          isBookmarksCollection = Some(true))
        val userCollectionId = UserCollection.insert(bookmarksCollection)
        try {
          IndexingService.index(bookmarksCollection.copy(_id = userCollectionId.get))
        } catch {
          case t => ErrorReporter.reportError(this.getClass.getName, t, "Could not index Bookmarks collection %s for newly created user %s".format(userCollectionId.get.toString, userName))
        }
      case Some(bookmarks) => // it's ok
    }

    session.put("connectedUserId", user.get._id.toString)
    session.put(AccessControl.ORGANIZATIONS, user.get.organizations.mkString(","))
    session.put(AccessControl.GROUPS, user.get.groups.mkString(","))

  }
}