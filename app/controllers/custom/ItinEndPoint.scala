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

package controllers.custom

import play.mvc.Controller
import controllers.ThemeAware
import scala.collection.JavaConversions.asScalaIterable

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 11/5/11 10:35 AM  
 */

object ItinEndPoint extends Controller with ThemeAware{

  import play.mvc.results.Result

  def search: Result = {
    import controllers.search.SearchService
    SearchService.getApiResult(request, theme)
  }

  def store(data: String): Result = {
    import play.data.Upload
    import java.util.List
    import models.{StoreResponse, DrupalEntity}
    import play.Logger
    import java.lang.String
    import xml.Elem

    val uploads: List[Upload] = request.args.get("__UPLOADS").asInstanceOf[java.util.List[play.data.Upload]]
    val body: String = params.get("body")
    val uploadedXml: Option[Elem] =
      if (uploads != null) Some(xml.XML.load(asScalaIterable(uploads).head.asStream()))
      else if (body != null && !body.isEmpty) Some(xml.XML.loadString(body))
      else None

    val xmlResponse = try {
      uploadedXml match {
        case x: Some[Elem] =>
          val response: StoreResponse = DrupalEntity.processStoreRequest(uploadedXml.get)((item, list) => DrupalEntity.insertInMongoAndIndex(item, list))
          StoreResponse(response.itemsParsed, response.coRefsParsed)
        case _ =>
          StoreResponse(success = false, errorMessage = "Unable to receive the file from the POST")
      }
    }
    catch {
      case ex: Exception =>
        Logger.error(ex, "Problem with the posted xml file")
        StoreResponse(success = false, errorMessage = "Unable to receive the xml-file from the POST")
    }

    val responseString =
    <response recordsProcessed={xmlResponse.itemsParsed.toString} linksProcessed={xmlResponse.coRefsParsed.toString}>
      <status>{if (xmlResponse.success) "succcess" else "failure"}</status>
      {if (!xmlResponse.errorMessage.isEmpty) <error> {xmlResponse.errorMessage}</error>}
    </response>
    if (!xmlResponse.success) response.status = new Integer(404)
    Xml(responseString.toString())
  }

}