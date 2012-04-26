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

package util

import extensions.ConfigurationException
import java.lang.String
import play.api.Play.current
import play.api.{Play, Logger}
import xml.{Node, XML}

//import scala.collection.JavaConversions._
import com.mongodb.casbah.commons.MongoDBObject
import models.{EmailTarget, PortalTheme}

/**
 * ThemHandler taking care of loading themes
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since 3/9/11 3:25 PM
 */
object ThemeHandler {

  private var themeList: Seq[PortalTheme] = List()

  def getThemeNames: java.util.Set[String] = {
    val set: java.util.Set[String] = new java.util.TreeSet[String]
    themeList.foreach(theme => set.add(theme.name))
    set
  }

  /**
   * Look into the database if we have some themes. If we don't attempt to load from YML.
   */
  def startup() {
    if (PortalTheme.count() == 0) {
      themeList = readThemesFromDisk
      themeList foreach {
        PortalTheme.insert(_)
      }
    } else {
      try {
        themeList = readThemesFromDatabase()
      } catch {
        case t: Throwable =>
          Logger.error("Error reading Themes from the database.", t)
          throw t
      }
    }

    if (!getDefaultTheme.isDefined) {
      throw ConfigurationException("No default theme could be found!")
    }
  }

  /**
   * Updates the themes in memory by reading them from the database
   */
  def update() {
    themeList = readThemesFromDatabase()
  }

  def readThemesFromDatabase(): Seq[PortalTheme] = PortalTheme.find(MongoDBObject()).toSeq

  def hasSingleTheme: Boolean = themeList.length == 1

  def hasTheme(themeName: String): Boolean = !themeList.filter(theme => theme.name == themeName).isEmpty

  def getDefaultTheme = themeList.filter(_.name == current.configuration.getString("themes.defaultTheme").getOrElse("default")).headOption

  def getByThemeName(name: String) = {
    val theme = themeList.filter(_.name.equalsIgnoreCase(name))
    if (!theme.isEmpty) theme.head
    else getDefaultTheme.get
  }

  def getByDomain(domain: String): PortalTheme = {
    if (Play.isDev) {
      startup()
    }
    if (hasSingleTheme) getDefaultTheme.get
    else {
      // fetch by longest matching subdomain
      themeList.foldLeft(getDefaultTheme.get) {
        (r: PortalTheme, c: PortalTheme) => {
          val rMatches = r.subdomain != None && domain.startsWith(r.subdomain.get)
          val cMatches = c.subdomain != None && domain.startsWith(c.subdomain.get)
          val rLonger = r.subdomain.get.length() > c.subdomain.get.length()

          if (rMatches && cMatches && rLonger) r
          else if (rMatches && cMatches && !rLonger) c
          else if (rMatches && !cMatches) r
          else if (cMatches && !rMatches) c
          else r // default
        }
      }
    }
  }

  def readThemesFromDisk: Seq[PortalTheme] = {
    val THEME_CONFIG_SUFFIX = "_themes.xml"
    val themeDefinitions = Play.getFile("conf/").listFiles().filter(f => f.isFile && f.getName.endsWith(THEME_CONFIG_SUFFIX))
    themeDefinitions.flatMap(f => parseThemeDefinition(XML.loadFile(f)))
  }

  private def parseThemeDefinition(root: Node): Seq[PortalTheme] = {
    for( theme <- root \\ "theme") yield {
      PortalTheme(
        name             = (theme \ "@name").text,
        subdomain        = Some((theme \ "subdomain").text),
        themeDir         = (theme \ "themeDir").text,
        defaultLanguage  = (theme \ "defaultLanguage").text,
        solrSelectUrl    = (theme \ "solrSelectUrl").text,
        facets           = Some((theme \ "facets").text),
        sortFields       = Some((theme \ "sortFields").text),
        apiWsKey         = (theme \ "apiWsKey").text.toBoolean,
        emailTarget = EmailTarget(
          (theme \ "emailTarget" \ "adminTo").text,
          (theme \ "emailTarget" \ "exceptionTo").text,
          (theme \ "emailTarget" \ "feedbackTo").text,
          (theme \ "emailTarget" \ "registerTo").text,
          (theme \ "emailTarget" \ "systemFrom").text,
          (theme \ "emailTarget" \ "feedbackFrom").text
        )
      )
    }
  }

}


