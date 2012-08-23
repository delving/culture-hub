package controllers.itin

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

import core.search.SearchService
import collection.JavaConverters._
import controllers.DomainConfigurationAware
import play.api.Logger
import models.{DrupalEntity, StoreResponse}
import play.api.mvc.{Controller, Action}
import play.api.libs.concurrent.Promise
import core.indexing.IndexingService

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 11/5/11 10:35 AM
 */

object ItinEndPoint extends Controller with DomainConfigurationAware {

  def search = DomainConfigured {
    Action {
      implicit request =>
        SearchService.getApiResult(request, List())
    }
  }

  def store: Action[scala.xml.NodeSeq] = DomainConfigured {
    Action(parse.tolerantXml) {
      implicit request => {
      Async {
          Promise.pure {
            val xmlResponse = try {
              val response: StoreResponse = DrupalEntity.dao.processStoreRequest(request.body, configuration)((item, list) => DrupalEntity.dao.insertInMongoAndIndex(item, list))
              StoreResponse(response.itemsParsed, response.coRefsParsed)
            } catch {
              case ex: Exception =>
                Logger.error("Problem with the posted xml file", ex)
                StoreResponse(success = false, errorMessage = "Unable to receive the xml-file from the POST")
            }

            <response recordsProcessed={xmlResponse.itemsParsed.toString} linksProcessed={xmlResponse.coRefsParsed.toString}>
              <status>
                {if (xmlResponse.success) "success" else "failure"}
              </status>{if (!xmlResponse.errorMessage.isEmpty) <error>
              {xmlResponse.errorMessage}
            </error>}
            </response>
          } map {
            response => Ok(response.toString()).as(XML)

          }
        }
      }
    }
  }

  def deleteAll() = DomainConfigured {
    Action {
      implicit request =>
        IndexingService.deleteByQuery("delving_recordType:\"drupal\"")
        DrupalEntity.dao.removeAll()
        Ok(<response><status>success</status></response>)

    }
  }

}