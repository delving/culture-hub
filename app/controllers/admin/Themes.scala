package controllers.admin

import play.mvc.results.Result
import extensions.JJson
import org.bson.types.ObjectId
import util.ThemeHandler
import com.mongodb.casbah.commons.MongoDBObject
import play.mvc.Before
import controllers.{Secure, ViewModel, DelvingController}
import models.{User, EmailTarget, PortalTheme}

/**
 * TODO add Access Control
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Themes extends DelvingController with Secure {

  @Before
  def checkAdmin(): Result = {
    val u = User.findByUsername(connectedUser) getOrElse(return Forbidden("Wrong user"))
    if(!u.isHubAdmin.getOrElse(false)) {
      reportSecurity("User %s tried to get access to themes admin".format(connectedUser))
      return Forbidden(&("user.secured.noAccess"))
    }
    Continue
  }

  def index(): Result = {
    val themeList = PortalTheme.find(MongoDBObject())
    Template('themes -> themeList.toList)
  }

  def load(id: String): Result = {
    PortalTheme.findOneByID(new ObjectId(id)) match {
      case None => Json(ThemeViewModel())
      case Some(theme) => Json(ThemeViewModel(id = Some(theme._id), name = theme.name, templateDir = theme.templateDir, isDefault = theme.isDefault, localisedQueryKeys = theme.localiseQueryKeys, hiddenQueryFilter = theme.hiddenQueryFilter, subdomain = theme.subdomain, displayName = theme.displayName, googleAnalyticsTrackingCode = theme.googleAnalyticsTrackingCode, addThisTrackingCode = theme.addThisTrackingCode, defaultLanguage = theme.defaultLanguage, colorScheme = theme.colorScheme, solrSelectUrl = theme.solrSelectUrl, cacheUrl = theme.cacheUrl, emailTarget = theme.emailTarget, homePage = theme.homePage, metadataPrefix = theme.metadataPrefix, text = theme.text, possibleQueryKeys = theme.localiseQueryKeys))
    }
  }

  def list(): AnyRef = {
    val themeList = PortalTheme.find(MongoDBObject())
    Json(Map("themes" -> themeList))
  }

  def themeUpdate(id: String): Result = Template('id -> Option(id))

  def themeSubmit(data: String): Result = {
    val theme = JJson.parse[ThemeViewModel](data)

    val persistedTheme = theme.id match {
      case None => {
        val inserted = PortalTheme.insert(PortalTheme(name = theme.name, templateDir = theme.templateDir, isDefault = theme.isDefault, localiseQueryKeys = theme.localisedQueryKeys, hiddenQueryFilter = theme.hiddenQueryFilter, subdomain = theme.subdomain, displayName = theme.displayName, googleAnalyticsTrackingCode = theme.googleAnalyticsTrackingCode, addThisTrackingCode = theme.addThisTrackingCode, defaultLanguage = theme.defaultLanguage, colorScheme = theme.colorScheme, solrSelectUrl = theme.solrSelectUrl, cacheUrl = theme.cacheUrl, emailTarget = theme.emailTarget, homePage = theme.homePage, metadataPrefix = theme.metadataPrefix, text = theme.text))
        inserted match {
          case Some(id) => Some(theme.copy(id = inserted))
          case None => None
        }
      }
      case Some(oid) => {
        val existing = PortalTheme.findOneByID(oid)
        if(existing == None) return NotFound(&("admin.themes.themeNotFound", oid))
        val updated = existing.get.copy(name = theme.name, templateDir = theme.templateDir, isDefault = theme.isDefault, localiseQueryKeys = theme.localisedQueryKeys, hiddenQueryFilter = theme.hiddenQueryFilter, subdomain = theme.subdomain, displayName = theme.displayName, googleAnalyticsTrackingCode = theme.googleAnalyticsTrackingCode, addThisTrackingCode = theme.addThisTrackingCode, defaultLanguage = theme.defaultLanguage, colorScheme = theme.colorScheme, solrSelectUrl = theme.solrSelectUrl, cacheUrl = theme.cacheUrl, emailTarget = theme.emailTarget, homePage = theme.homePage, metadataPrefix = theme.metadataPrefix, text = theme.text)
        PortalTheme.save(updated)
        Some(theme)
      }
    }

    persistedTheme match {
      case Some(theTheme) => {
        ThemeHandler.update()
        Json(theTheme)
      }
      case None => Error(&("admin.themes.saveError", theme.name))
    }

  }

}

case class ThemeViewModel(id: Option[ObjectId] = None,
                          name: String = "",
                          templateDir: String = "",
                          isDefault: Boolean = false,
                          localisedQueryKeys: List[String] = List(),
                          possibleQueryKeys: List[String] = List(),
                          hiddenQueryFilter: Option[String] = Some(""),
                          subdomain: Option[String] = None,
                          displayName: String = "",
                          googleAnalyticsTrackingCode: Option[String] = Some(""),
                          addThisTrackingCode: Option[String] = Some(""),
                          defaultLanguage: String = "",
                          colorScheme: String = "",
                          solrSelectUrl: String = "http://localhost:8983/solr",
                          cacheUrl: String = "http://localhost:8983/services/image?",
                          emailTarget: EmailTarget = EmailTarget(),
                          homePage: Option[String] = None,
                          metadataPrefix: Option[String] = Some("icn"),
                          text: String = "",
                          errors: Map[String, String] = Map.empty[String, String]) extends ViewModel