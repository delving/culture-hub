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

import play.api.mvc.Action
import core.opendata.OaiPmhService
import play.api.libs.concurrent.Promise

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Services extends DelvingController {

  def oaipmh(orgId: String, accessKey: Option[String]) = Action {
    implicit request =>
      Async {
        val oaiPmhService = new OaiPmhService(request.queryString, request.uri, orgId, accessKey)
        Promise.pure(oaiPmhService.parseRequest).map {
          response =>

            if(!request.path.contains("api")) {
              warning("Using deprecated API call " + request.uri)
            }

            Ok(response).as(XML)
        }
      }
  }

  //  def oaipmhSecured(orgId: Option[String] = Some("delving"), accessKey: String)  = Root { // todo implement this properly in the routes
  //      Action {
  //        implicit request =>
  //          val oaiPmhService = new OaiPmhService(request, accessKey)
  //          Ok(oaiPmhService.parseRequest).as(XML)
  //      }
  //  }
}