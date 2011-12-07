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

import play.mvc.results.Result
import play.Play

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since 1/2/11 10:09 PM
 */
object Images extends DelvingController {

  def view: AnyRef = {

    val image = params.get("image")

    // just for testing
    val smallballs = Play.applicationPath.getAbsolutePath + "/public/images/smallballs.tif"

    if (image.isEmpty || image.equalsIgnoreCase("smallballs")) Template('image -> smallballs)
    else Template("/Image/view.html", 'image -> (image))

  }

  def iipsrv(): Result = {
    // TODO this should be a permanent redirect
    Redirect(Play.configuration.getProperty("iipsrv.url") + "?" + request.querystring, true)
  }

}