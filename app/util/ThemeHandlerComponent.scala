/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.1 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package util

import org.apache.log4j.Logger
import java.lang.String
import play.Play
import play.mvc.Http
import scala.collection.JavaConversions._
import play.test._
import cake.MetadataModelComponent
import models.PortalTheme

trait ThemeHandlerComponent {
  this: MetadataModelComponent =>
  val themeHandler: ThemeHandler


  /**
   *
   * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
   * @since 3/9/11 3:25 PM
   */
  class ThemeHandler {

    private val log: Logger = Logger.getLogger(getClass)

    private var themeList: Seq[PortalTheme] = List()

    private val defaultQueryKeys = List("dc.title", "dc.description", "dc.creator", "dc.subject", "dc.date") // todo add more default cases

    private val getThemeList: Seq[PortalTheme] = if (themeList.isEmpty) {
      startup()
      themeList
    } else themeList

    def getThemeNames: java.util.Set[String] = {
      val set: java.util.Set[String] = new java.util.TreeSet[String]
      getThemeList.foreach(theme => set.add(theme.name))
      set
    }

    private lazy val debug = Play.configuration.getProperty("debug").trim.toBoolean

    /**
     * Look into the database if we have some themes. If we don't attempt to load from YML.
     */
    def startup() {

      if (PortalTheme.count() == 0) {
        if (updateFromDisk()) {
          themeList foreach {
            PortalTheme.insert(_)
          }
        }
      } else {
        themeList = readThemesFromDatabase()
      }
    }

    /**
     * Sync the in-memory state with the state in the database
     */
    def readThemesFromDatabase(): List[PortalTheme] = {
      PortalTheme.findAll
    }

    def updateFromDisk(): Boolean = {
      try {
        val newThemes = loadThemesYaml()
        if (themeList != newThemes) themeList = newThemes
      } catch {
        case ex: Throwable => {
          ex.printStackTrace
          log.error("Error updating themes from YAML descriptor")
          return false
        }

      }
      true
    }

    def hasSingleTheme: Boolean = getThemeList.length == 1

    def hasTheme(themeName: String): Boolean = !getThemeList.filter(theme => theme.name == themeName).isEmpty

    def getDefaultTheme = getThemeList.filter(_.isDefault == true).head

    def getByThemeName(name: String) = {
      val theme = getThemeList.filter(_.name.equalsIgnoreCase(name))
      if (!theme.isEmpty) theme.head
      else getDefaultTheme
    }

    def getByBaseUrl(baseUrl: String): PortalTheme = {
      val theme = getThemeList.filter(_.baseUrl.get.equalsIgnoreCase(baseUrl))
      if (!theme.isEmpty) theme.head
      else getDefaultTheme
    }

    def getByBaseUrl(request: Http.Request): PortalTheme = getByBaseUrl(request.host)

    def getByRequest(request: Http.Request): PortalTheme = {
      if (hasSingleTheme) getDefaultTheme
      else if (debug && request.params._contains("theme")) getByThemeName(request.params.get("theme"))
      else getByBaseUrl(request)
    }

    private[util] def loadThemesYaml(): Seq[PortalTheme] = {

      def getProperty(prop: String): String = Play.configuration.getProperty(prop).trim

      val themeFileName = getProperty("portal.theme.file")

      if (themeFileName == null) {
        log.fatal("portal.theme.file path must be defined in application.conf");
        System.exit(1);
      }

      val themes = for (theme <- YamlLoader.load[List[PortalTheme]](themeFileName)) yield {
        theme.copy(
          localiseQueryKeys = if (theme.localiseQueryKeys == null) defaultQueryKeys else defaultQueryKeys ++ theme.localiseQueryKeys
        )
      }
      themes
    }
  }

}



