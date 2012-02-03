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

import dos.StoredFile
import organization.CMS
import play.Play
import com.mongodb.casbah.commons.MongoDBObject
import scala.collection.JavaConversions._
import play.mvc._
import results.Result
import models._
import org.bson.types.ObjectId
import play.data.validation.Validation
import extensions.AdditionalActions
import play.i18n.{Lang, Messages}
import util.{ThemeInfoReader, ThemeHandler, LocalizedFieldNames, ProgrammerException}

/**
 * Root controller for culture-hub. Takes care of checking URL parameters and other generic concerns.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait DelvingController extends Controller with ModelImplicits with AdditionalActions with Logging with ThemeAware with UserAuthentication with Internationalization {

  @Before(priority = 0) def baseAction(): Result = {

    // CSRF check
    if(request.method == "POST" && Play.id != "test") {
      val authenticityTokenParam = params.get("authenticityToken")
      val CSRFHeader = request.headers.get("x-csrf-token")
      if ((authenticityTokenParam == null && CSRFHeader == null) || (authenticityTokenParam != null && !(authenticityTokenParam == session.getAuthenticityToken)) || (CSRFHeader != null && !(CSRFHeader.value() == session.getAuthenticityToken))) {
        return Forbidden("Bad authenticity token")
      }
    }

    // Connected user
    User.findByUsername(connectedUser).map {
      u => {
        renderArgs += ("fullName", u.fullname)
        renderArgs += ("userName", u.userName)
        renderArgs += ("userId", u._id)
        renderArgs += ("authenticityToken", session.getAuthenticityToken)
        renderArgs += ("organizations", u.organizations)
        renderArgs += ("email", u.email)
        renderArgs += ("isNodeAdmin", u.nodesAdmin.contains(getNode))

        // refresh session parameters
        session.put(AccessControl.ORGANIZATIONS, u.organizations.mkString(","))
        session.put(AccessControl.GROUPS, u.groups.mkString(","))
      }
    }

    // Language

    // if a lang param is passed, this is a request to explicitly change the language
    // and will change it in the user's cookie
    val lang: String = params.get("lang")
    if(lang != null) {
      Lang.change(lang)
    }

    // if there is no language for this cookie / user set, set the default one from the PortalTheme
    val cn: String = Play.configuration.getProperty("application.lang.cookie", "PLAY_LANG")
    if (request.cookies.containsKey(cn)) {
      val localeFromCookie: String = request.cookies.get(cn).value
      if (localeFromCookie == null || localeFromCookie != null && localeFromCookie.trim.length == 0) {
        Lang.change(theme.defaultLanguage)
      }
    }

    // Menu entries
    val mainMenuEntries = MenuEntry.findEntries(theme.name, CMS.MAIN_MENU).filterNot(!_.title.contains(Lang.get())).map(e => (Map(
        "title" -> e.title(Lang.get()),
        "page" -> e.targetPageKey.getOrElse("")))
    ).toList
    renderArgs += ("menu", mainMenuEntries)

    // Browsed user
    Option(params.get("user")).map {
      userName =>
        val user = User.findByUsername(userName)
        user match {
          case Some(u) =>
            renderArgs += ("browsedFullName", u.fullname)
            renderArgs += ("browsedUserId", u._id)
            renderArgs += ("browsedUserName", u.userName)
          case None =>
            renderArgs += ("browsedUserNotFound", userName)
      }
    }

    Option(params.get("orgId")).map {
      orgId =>
        val orgName = Organization.fetchName(orgId)
        renderArgs += ("browsedOrgName", orgName)
    }

    if(!browsedUserExists) return NotFound(&("delvingcontroller.userNotFound", renderArgs.get("browsedUserNotFound", classOf[String])))

    Continue
  }

  // ~~~ convenience methods to access user information

  @Util def getUser(userName: String): Either[Result, User] = User.findOne(MongoDBObject("userName" -> userName, "isActive" -> true)) match {
    case Some(user) => Right(user)
    case None => Left(NotFound(&("delvingcontroller.userNotFound", userName)))
  }

  @Util def connectedUserId = renderArgs.get("userId", classOf[ObjectId])

  @Util def browsedUserName: String = renderArgs.get("browsedUserName", classOf[String])

  @Util def browsedUserId: ObjectId = renderArgs.get("browsedUserId", classOf[ObjectId])

  @Util def browsedFullName: String = renderArgs.get("browsedFullName", classOf[String])

  @Util def browsedUserExists: Boolean = renderArgs.get("browsedUserNotFound") == null

  @Util def browsedIsConnected: Boolean = browsedUserName == connectedUser

  @Util def browsingUser: Boolean = browsedUserName != null

  @Util def isNodeAdmin: Boolean = renderArgs.get("isNodeAdmin", classOf[Boolean])

  // ~~~ convenience methods

  @Util def listPageTitle(itemName: String) = if(browsingUser) &("listPageTitle.%s.user".format(itemName), browsedFullName) else &("listPageTitle.%s.all".format(itemName))

  @Util def validate(viewModel: AnyRef): Option[Map[String, String]] = {
    import scala.collection.JavaConversions.asScalaIterable

    if(!Validation.valid("object", viewModel).ok || Validation.hasErrors) {
      val fieldErrors = asScalaIterable(Validation.errors).filter(_.getKey.contains(".")).map { error => (error.getKey.split("\\.")(1), error.message()) }
      val globalErrors = asScalaIterable(Validation.errors).filterNot(_.getKey.contains(".")).map { error => ("global", error.message()) }
      val errors = fieldErrors ++ globalErrors
      Some(errors.toMap)
    } else {
      None
    }
  }

  @Util def getNode = play.Play.configuration.getProperty("culturehub.nodeName")


  // ~~~ error handling

  @Finally()
  def handleEOF(t: Throwable) {
    if(t != null) {
      ErrorReporter.reportError(request, params, connectedUser, t, "Something went wrong")
    }
  }

}


trait ThemeAware { self: Controller =>

  val localizedFieldNames = new LocalizedFieldNames

  private val themeThreadLocal: ThreadLocal[PortalTheme] = new ThreadLocal[PortalTheme]
  private val lookupThreadLocal: ThreadLocal[LocalizedFieldNames.Lookup] = new ThreadLocal[LocalizedFieldNames.Lookup]

  @Util implicit def theme = themeThreadLocal.get()

  @Util implicit def lookup = lookupThreadLocal.get()

  @Util def viewUtils: ViewUtils = renderArgs.get("viewUtils").asInstanceOf[ViewUtils]
  
  @Before(priority = 0)
  def setTheme() {
    val portalTheme = if(Http.Request.current() == null) ThemeHandler.getDefaultTheme.get else ThemeHandler.getByRequest(Http.Request.current())
    themeThreadLocal.set(portalTheme)
    lookupThreadLocal.set(localizedFieldNames.createLookup(portalTheme.localiseQueryKeys))
    renderArgs.put("theme", theme)
  }

  @Before(priority = 1) def setViewUtils() {
    renderArgs += ("viewUtils", new ViewUtils(theme))
  }

  @Finally
  def cleanup() {
    themeThreadLocal.remove()
    lookupThreadLocal.remove()
  }

}

trait Internationalization {

  import play.i18n.Messages

  def &(msg: String, args: String*) = Messages.get(msg, args : _ *)

}

/**
 * This class will hold all sort of utility methods that need to be called form the templates. It is meant to be initalized at each request
 * and be passed to the view using the renderArgs.
 *
 * It should replace the old views.context package object
 */
class ViewUtils(theme: PortalTheme) {

  def themeProperty(property: String) = {
    themeProperty[String](property, classOf[String])
  }

  def themeProperty[T](property: String, clazz: Class[T] = classOf[String])(implicit mf: Manifest[T]): T = {
    val value: String = ThemeInfoReader.get(property, theme.name) match {
      case Some(prop) => prop
      case None =>
        ThemeInfoReader.get(property, "default") match {
          case Some(prop) => prop
          case None => throw new ProgrammerException("No default value, nor actual value, defined for property '%s' in application.conf".format(property))
        }
    }

    val INT = classOf[Int]
    val result = mf.erasure match {
      case INT => Integer.parseInt(value)
      case _ => value
    }

    result.asInstanceOf[T]
  }

  def getKey(msg: String, args: String): String = {
    Messages.get(msg, args)
  }
  def getKey(msg: String): String = Messages.get(msg)

}