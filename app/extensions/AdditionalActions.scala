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

package extensions

import play.mvc.Http.{Response, Request}
import models.User
import play.mvc.Controller
import play.mvc.results._

/**
 * This trait provides additional actions that can be used in controllers
 */
trait AdditionalActions extends Extensions {
  self: Controller =>

  def JsonBadRequest(data: AnyRef): Result = {
    response.status = 400
    Json(data)
  }

  def RenderKml(entity: AnyRef) = new RenderKml(entity)
}

class RenderKml(entity: AnyRef) extends Result {
  def apply(request: Request, response: Response) {
    val doc = entity match {
      case u: User => KMLSerializer.toKml(Option(u))
      case _ => KMLSerializer.toKml(None)
    }
    new RenderXml(doc.toString())(request, response)
  }
}