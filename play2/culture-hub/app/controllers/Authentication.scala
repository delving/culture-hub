package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.templates.groovy.GroovyTemplates
import core.ThemeAware
import play.api.libs.Crypto
import play.libs.Time
import play.api.i18n.Messages
import util.MissingLibs
import java.util.Date
import models.{Visibility, UserCollection, User}
import core.indexing.IndexingService

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Authentication extends Controller with GroovyTemplates with ThemeAware {

  val USERNAME = "userName"
  val REMEMBER_COOKIE = "rememberme"
  val AT_KEY = "___AT" // authenticity token

  case class Auth(userName: String, password: String)

  val loginForm = Form(
    tuple(
      "userName" -> nonEmptyText,
      "password" -> nonEmptyText,
      "remember" -> boolean
    ) verifying(Messages("authentication.error"), result => result match {
      case (u, p, r) => User.connect(u, p)
    }))

  def login = Themed {
    Action {
      implicit request =>
        if(session.get("userName").isDefined) {
          Redirect(controllers.routes.Application.index)
        } else {
          Ok(Template('loginForm -> loginForm))
        }
    }
  }

  def logout = Action {
    Redirect(routes.Authentication.login).withNewSession.flashing(
      "success" -> Messages("authentication.logout")
    ).discardingCookies(REMEMBER_COOKIE)
  }

  /**
   * Handle login form submission.
   */
  def authenticate = Themed { Action {
    implicit request =>
      loginForm.bindFromRequest.fold(
        formWithErrors => BadRequest(Template("/Authentication/login.html", 'loginForm -> formWithErrors)),
        user => {
          // on authenticated
          val maybeUser = User.findByUsername(user._1)

          User.findBookmarksCollection(user._1) match {
            case None =>
              // create default bookmarks collection
              val bookmarksCollection = UserCollection(
                TS_update = new Date(),
                userName = user._1,
                name = "Bookmarks",
                description = "Bookmarks",
                visibility = Visibility.PRIVATE,
                thumbnail_id = None,
                thumbnail_url = None,
                isBookmarksCollection = Some(true))
              val userCollectionId = UserCollection.insert(bookmarksCollection)
              try {
                IndexingService.index(bookmarksCollection.copy(_id = userCollectionId.get))
              } catch {
                case t => ErrorReporter.reportError(this.getClass.getName, t, "Could not index Bookmarks collection %s for newly created user %s".format(userCollectionId.get.toString), theme)
              }
            case Some(bookmarks) => // it's ok
          }

          val action = (request.session.get("uri") match {
            case Some(uri) => Redirect(uri)
            case None => Redirect(controllers.routes.Application.index)
          }).withSession(
            USERNAME -> user._1,
            "connectedUserId" -> maybeUser.get._id.toString,
            AccessControl.ORGANIZATIONS -> maybeUser.get.organizations.mkString(","),
            AccessControl.GROUPS -> maybeUser.get.groups.mkString(","),
            AT_KEY -> authenticityToken)

          if (user._3) {
            action.withCookies(Cookie(
              name = REMEMBER_COOKIE,
              value = Crypto.sign(user._1) + "-" + user._1,
              maxAge = Time.parseDuration("30d")
            ))
          } else {
            action
          }
        }
      )
  }
  }

  private def authenticityToken = Crypto.sign(MissingLibs.UUID)

}


object AccessControl {

  val ORGANIZATIONS = "organizations"
  val GROUPS = "groups"

}