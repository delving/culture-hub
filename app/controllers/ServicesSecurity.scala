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
import play.mvc.Scope.Session

class ServicesSecurity extends Security with Internationalization {

  def authenticate(username: String, password: String): Boolean = {
    User.connect(username, password)
  }

  def onAuthenticated(username: String, session: Session) {
    val user = User.findByUsername(username)
    if(user == None) {
      throw new RuntimeException(&("servicessecurity.userNotFound", username))
    }
    session.put("connectedUserId", user.get._id.toString)
    session.put(AccessControl.ORGANIZATIONS, user.get.organizations.mkString(","))
    session.put(AccessControl.GROUPS, user.get.groups.mkString(","))

  }
}