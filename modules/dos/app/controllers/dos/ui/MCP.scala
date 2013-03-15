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

package controllers.dos.ui

import play.api.mvc._
import java.io.File
import eu.delving.templates.scala.GroovyTemplates
import extensions.Extensions
import play.api.libs.MimeTypes

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object MCP extends Controller with Extensions with GroovyTemplates {

  def index = Action {
    implicit request => Ok(Template)
  }

  def browse(path: String) = Action {
    implicit request =>
      val f = new File(path)
      if (!f.exists()) {
        InternalServerError("Directory '%s' does not exist".format(path))
      } else if (!f.isDirectory) {
        InternalServerError("Trying to browse a file: " + path)
      } else {
        val files = if (f.listFiles == null) {
          List()
        } else {
          f.listFiles.map(f => BrowserFile(
            path = f.getAbsolutePath,
            name = f.getName,
            isDir = f.isDirectory,
            contentType = MimeTypes.forFileName(f.getName).getOrElse("unknown/unknown")
          )).sortBy(!_.isDir)
        }
        Ok(Template("/dos/ui/MCP/index.html", 'files -> files))
      }
  }

}

case class BrowserFile(path: String,
    name: String,
    isDir: Boolean,
    contentType: String) {

  def isImage = contentType.contains("image")

  def id = if (name.contains(".") && !name.startsWith(".")) name.split("\\.")(0) else name
}