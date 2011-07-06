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
import xml.{Node, NodeSeq, Elem, XML}
import play.Play
import play.mvc.Http
import eu.delving.metadata.{MetadataModelImpl, RecordDefinition, MetadataModel}
import scala.collection.JavaConversions._

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 3/9/11 3:25 PM  
 */

class ThemeHandler {
  private val log: Logger = Logger.getLogger(getClass)

  private lazy val themeList: Seq[PortalTheme] = loadThemes()

  private val defaultQueryKeys = List("dc.title","dc.description","dc.creator","dc.subject", "dc.date") // todo add more default cases

  def getThemeNames: java.util.Set[String] = {
    val set :java.util.Set[String] = new java.util.TreeSet[String]
    themeList.foreach(theme => set.add(theme.name))
    set
  }

  private lazy val debug = Play.configuration.getProperty("debug").trim.toBoolean

  def hasSingleTheme : Boolean = themeList.length == 1

  def hasTheme(themeName : String) : Boolean = !themeList.filter(theme => theme.name == themeName).isEmpty

  def getDefaultTheme = themeList.filter(_.isDefault == true).head

  def getByThemeName(name : String) = {
    val theme = themeList.filter(_.name.equalsIgnoreCase(name))
    if (!theme.isEmpty) theme.head
    else getDefaultTheme
  }

  def getByBaseUrl(baseUrl : String) : PortalTheme = {
    val theme = themeList.filter(_.baseUrl.equalsIgnoreCase(baseUrl))
    if (!theme.isEmpty) theme.head
    else getDefaultTheme
  }

  def getByBaseUrl(request : Http.Request) : PortalTheme = getByBaseUrl(request.host)

  def getByRequest(request : Http.Request) : PortalTheme = {
    if (hasSingleTheme) getDefaultTheme
    else if (debug && request.params._contains("theme")) getByThemeName(request.params.get("theme"))
    else getByBaseUrl(request)
  }

  private[util] def loadThemes() : Seq[PortalTheme] = {

    def getProperty(prop : String) : String = Play.configuration.getProperty(prop).trim
    def getRecordDefinition(prefix : String) : RecordDefinition = {
      try {
        metadataModel.getRecordDefinition(prefix)
      }
      catch {
        case ex : Exception => metadataModel.getRecordDefinition
      }
    }

    val themeFilePath = getProperty("portal.theme.file")

    if (themeFilePath == null) {
        log.fatal("portal.theme.file path must be defined in -Dlaunch.properties=/path/to/property/file");
        System.exit(1);
    }

    def createPortalTheme(node : Node, isDefault : Boolean = false) : PortalTheme = {
      val templateDir = node \\ "templateDir"
      def getNodeText(label : String) : String = (node \\ label).text
      def getBooleanNodeText(label : String) : Boolean = try {(node \\ label).text.toBoolean} catch {case ex : Exception => false}
      def getNodeTextAsArray(label : String)  : Array[String] = (node \\ label).text.trim().split(" *, *")

      def createEmailTarget(node: Node): EmailTarget = {
        EmailTarget(
          adminTo = getNodeText("adminTo"),
          exceptionTo = getNodeText("exceptionTo"),
          feedbackTo = getNodeText("feedbackTo"),
          registerTo = getNodeText("registerTo"),
          systemFrom = getNodeText("systemFrom"),
          feedbackFrom = getNodeText("feedbackFrom")
        )
      }

      PortalTheme(
        name = getNodeText("name"),
        templateDir = getNodeText("templateDir"),
        isDefault = isDefault,
        localiseQueryKeys = defaultQueryKeys.toArray ++ getNodeTextAsArray("localiseQueryKeys"),
        hqf = getNodeText("hiddenQueryFilter"),
        baseUrl = getNodeText("portalBaseUrl"),
        solrSelectUrl = getNodeText("solrSelectUrl"),
        cacheUrl = getNodeText("cacheUrl"),
        displayName = getNodeText("portalDisplayName"),
        gaCode = getNodeText("googleAnalyticsTrackingCode"),
        addThisCode = getNodeText("addThisTrackingCode"),
        defaultLanguage = getNodeText("defaultLanguage"),
        colorScheme = getNodeText("colorScheme"),
        emailTarget = createEmailTarget(node) ,
        homePage = getNodeText("homePage"),
        recordDefinition = getRecordDefinition(getNodeText("metadataPrefix"))
      )
    }

    val themes: Elem = XML.loadFile(themeFilePath)
    val themeList: NodeSeq = themes \\ "theme"
    val portalThemeSeq = themeList.map {
      themeNode =>
        val isDefault : Boolean = themeNode.attributes.get("default").head.text.toBoolean
//      themeNode.child.filter(!_.label.startsWith("#PCDATA")).foreach(nd => println (nd.label + nd.text))
        createPortalTheme(themeNode, isDefault)
    }
    if (portalThemeSeq.isEmpty) {
        log.fatal("Error loading themes from " + themeFilePath)
        System.exit(1)
    }
    portalThemeSeq
  }

  // TODO fix this. figure out a way to do something like DI
  val metadataModel: MetadataModelImpl = new MetadataModelImpl
  metadataModel.setDefaultPrefix(Play.configuration.getProperty("services.harvindexing.prefix"))
  metadataModel.setRecordDefinitionResources(List("/abm-record-definition.xml", "/icn-record-definition.xml", "/ese-record-definition.xml"))


}
case class PortalTheme (
  name : String,
  templateDir : String,
  isDefault : Boolean = false,
  localiseQueryKeys : Array[String] = Array(),
  hqf : String = "",
  baseUrl : String = "",
  displayName: String = "default",
  gaCode: String = "",
  addThisCode : String = "",
  defaultLanguage : String = "en",
  colorScheme : String = "azure",
  solrSelectUrl : String = "http://localhost:8983/solr",
  cacheUrl : String = "http://localhost:8983/services/image?",
  emailTarget : EmailTarget = EmailTarget(),
  homePage : String = "",
  recordDefinition : RecordDefinition
) {
  def getName = name
  def getTemplateDir = templateDir
  def getHiddenQueryFilters = hqf
  def getSolrSelectUrl = solrSelectUrl
  def getBaseUrl = baseUrl
  def getCacheUrl = cacheUrl
  def getDisplayName = displayName
  def getGaCode = gaCode
  def getAddThisCode = addThisCode
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
