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
import eu.delving.metadata.RecordDefinition
import scala.collection.JavaConversions._
import play.test._
import cake.MetadataModelComponent

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

    def getThemeNames: java.util.Set[String] = {
      val set: java.util.Set[String] = new java.util.TreeSet[String]
      themeList.foreach(theme => set.add(theme.name))
      set
    }

    private lazy val debug = Play.configuration.getProperty("debug").trim.toBoolean

    def update(): Boolean = {
      try {
        val newThemes = loadThemesYaml()
        if (themeList != newThemes) themeList = newThemes
      } catch {
        case _ => {
          log.error("Error updating themes")
          return false
        }

      }
      true
    }

    def hasSingleTheme: Boolean = themeList.length == 1

    def hasTheme(themeName: String): Boolean = !themeList.filter(theme => theme.name == themeName).isEmpty

    def getDefaultTheme = themeList.filter(_.isDefault == true).head

    def getByThemeName(name: String) = {
      val theme = themeList.filter(_.name.equalsIgnoreCase(name))
      if (!theme.isEmpty) theme.head
      else getDefaultTheme
    }

    def getByBaseUrl(baseUrl: String): PortalTheme = {
      val theme = themeList.filter(_.baseUrl.equalsIgnoreCase(baseUrl))
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

      def buildRecordDefinition(prefix: String): RecordDefinition = {
        try {
          metadataModel.getRecordDefinition(prefix)
        }
        catch {
          case ex: Exception => metadataModel.getRecordDefinition
        }
      }


      val themeFileName = getProperty("portal.theme.file")

      if (themeFileName == null) {
        log.fatal("portal.theme.file path must be defined in application.conf");
        System.exit(1);
      }

      val themes = for (theme <- Yaml[List[PortalTheme]](themeFileName)) yield {
        theme.copy(
          localiseQueryKeys = if (theme.localiseQueryKeys == null) defaultQueryKeys.toArray else defaultQueryKeys.toArray ++ theme.localiseQueryKeys,
          recordDefinition = buildRecordDefinition(theme.metadataPrefix)
        )
      }
      themes
    }
  }

}

case class PortalTheme(
                              name: String,
                              templateDir: String,
                              isDefault: Boolean = false,
                              localiseQueryKeys: Array[String] = Array(),
                              hiddenQueryFilter: String = "",
                              baseUrl: String = "",
                              displayName: String = "default",
                              googleAnalyticsTrackingCode: String = "",
                              addThisTrackingCode: String = "",
                              defaultLanguage: String = "en",
                              colorScheme: String = "azure",
                              solrSelectUrl: String = "http://localhost:8983/solr",
                              cacheUrl: String = "http://localhost:8983/services/image?",
                              emailTarget: EmailTarget = EmailTarget(),
                              homePage: String = "",
                              metadataPrefix: String = "",
                              recordDefinition: RecordDefinition

                              ) {
  def getName = name

  def getTemplateDir = templateDir

  def getHiddenQueryFilters = hiddenQueryFilter

  def getSolrSelectUrl = solrSelectUrl

  def getBaseUrl = baseUrl

  def getCacheUrl = cacheUrl

  def getDisplayName = displayName

  def getGaCode = googleAnalyticsTrackingCode

  def getAddThisCode = addThisTrackingCode

  def getDefaultLanguage = defaultLanguage

  def getColorScheme = colorScheme

  def withLocalisedQueryString = localiseQueryKeys.isEmpty

  def getLocaliseQueryKeys = localiseQueryKeys

  def getEmailTarget = emailTarget

  def getHomePage = homePage

  def getRecordDefinition = recordDefinition
}

case class EmailTarget(
                              adminTo: String = "test-user@delving.eu",
                              exceptionTo: String = "test-user@delving.eu",
                              feedbackTo: String = "test-user@delving.eu",
                              registerTo: String = "test-user@delving.eu",
                              systemFrom: String = "noreply@delving.eu",
                              feedbackFrom: String = "noreply@delving.eu"
                              ) {
  def getAdminTo = adminTo

  def getExceptionTo = exceptionTo

  def getFeedbackTo = feedbackTo

  def getRegisterTo = registerTo

  def getSystemFrom = systemFrom

  def getFeebackFrom = feedbackFrom
}
