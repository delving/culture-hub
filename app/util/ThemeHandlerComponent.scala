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
import cake.MetadataModelComponent
import models.PortalTheme

trait ThemeHandlerComponent {
  this: MetadataModelComponent =>
  val themeHandler: ThemeHandler


  /**
   * ThemHandler taking care of loading themes (initially from YML, then from mongo)
   *
   * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
   * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
   * @since 3/9/11 3:25 PM
   */
  class ThemeHandler {

    private val log: Logger = Logger.getLogger(getClass)

    private var themeList: Seq[PortalTheme] = List()

    private val defaultQueryKeys = List("dc.title", "dc.description", "dc.creator", "dc.subject", "dc.date") // todo add more default cases

    def getThemeNames: java.util.Set[String] = {
      val set: java.util.Set[String] = new java.util.TreeSet[String]
      themeList.foreach(theme => set.add(theme.name))
      set
    }

    private lazy val debug = Play.configuration.getProperty("debug").trim.toBoolean

    /**
     * Look into the database if we have some themes. If we don't attempt to load from YML.
     */
    def startup() {

      if (PortalTheme.count() == 0) {
        themeList = readThemesFromDisk()
        themeList foreach {
          PortalTheme.insert(_)
        }
      } else {
        themeList = readThemesFromDatabase()
      }

      if(getDefaultTheme.isEmpty) {
        throw new RuntimeException("No themes could be found!") // this should be some kind of custom startup exception
      }
    }

    def readThemesFromDatabase(): Seq[PortalTheme] = {
      PortalTheme.findAll
    }

    def readThemesFromDisk(): Seq[PortalTheme] = {
      try {
        loadThemesYaml()
      } catch {
        case ex: Throwable => {
          log.error("Error updating themes from YAML descriptor")
          throw new RuntimeException("Error updating themes from YAML descriptor", ex)
        }
      }
    }

    def hasSingleTheme: Boolean = themeList.length == 1

    def hasTheme(themeName: String): Boolean = !themeList.filter(theme => theme.name == themeName).isEmpty

    def getDefaultTheme = themeList.filter(_.isDefault == true).headOption

    def getByThemeName(name: String) = {
      val theme = themeList.filter(_.name.equalsIgnoreCase(name))
      if (!theme.isEmpty) theme.head
      else getDefaultTheme.get
    }

    def getByBaseUrl(baseUrl: String): PortalTheme = {
      val theme = themeList.filter(_.baseUrl.equalsIgnoreCase(baseUrl))
      if (!theme.isEmpty) theme.head
      else getDefaultTheme.get
    }

    def getByBaseUrl(request: Http.Request): PortalTheme = getByBaseUrl(request.host)

    def getByRequest(request: Http.Request): PortalTheme = {
      if (hasSingleTheme) getDefaultTheme.get
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



