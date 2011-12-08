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

import play.mvc.Controller
import java.io.File
import play.libs.IO
import play.mvc.results.{RenderBinary, Result}
import play.{Logger, Play}

/**
 * Helper controller to pre-process assets such as jquery templates
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Asset extends Controller with Logging with Internationalization {

  def get(path: String): Result = {
    val f = new File(Play.applicationPath + path)
    if(!f.exists()) return NotFound("Asset at path %s not found".format(path))
    val content = IO.readContentAsString(f)
    val messages = "\\&\\{([^\\}]*)\\}".r.findAllIn(content).matchData.map(m => (m.group(0), m.group(1))).map {
      m =>
        val elems: Array[String] = m._2.split(",").map(e => e.trim.substring(1, e.trim.length() -1))
        val key: String = elems(0)
        val args: Array[String] = elems.slice(1, elems.length)
        (m._1, key, args)
    }
    Text(messages.foldLeft(content) { (r, c) => r.replace(c._1, &(c._2, c._3 : _*))})
  }

  def serveTheme(relativePath: String, theme: String): Result = {
    val available = Play.configuration.getProperty("themes.available", "default").split(",")
    if(available.contains(theme)) {
      return Continue
    }

    val additionalThemes = Option(Play.configuration.getProperty("themes.additionalThemesDir"))
    additionalThemes match {
      case Some(s) =>
        val f = new File(Play.applicationPath + "/" + s).getCanonicalFile
        if(!f.exists() || !f.isDirectory) {
          return Error("Incorrectly configured additional themes directory %s".format(s))
        }
        val r = new File(f, theme + "/" + relativePath)
        if(!r.exists()) return NotFound
        new RenderBinary(r)
      case None =>
        NotFound
    }
  }

}