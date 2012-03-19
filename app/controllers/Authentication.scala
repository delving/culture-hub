package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import eu.delving.templates.scala.GroovyTemplates
import play.api.libs.Crypto
import play.libs.Time
import play.api.i18n.Messages
import extensions.MissingLibs
import java.util.Date
import core.indexing.IndexingService
import core.{HubServices, ThemeAware}
import models.{HubUser, Visibility, UserCollection}

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
      case (u, p, r) => HubServices.authenticationService.connect(u, p)
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
    Redirect(routes.Authentication.login).withNewSession.discardingCookies(REMEMBER_COOKIE).flashing(
      "success" -> Messages("authentication.logout")
    )
  }

  /**
   * Handle login form submission.
   */
  def authenticate: Action[AnyContent] = Themed {Action {
    implicit request =>
      loginForm.bindFromRequest.fold(
        formWithErrors => BadRequest(Template("/Authentication/login.html", 'loginForm -> formWithErrors)),
        user => {
          // first check if the user exists in this hub
          val u: Option[HubUser] = HubUser.findByUsername(user._1).orElse {
            // create a local user
            HubServices.userProfileService.getUserProfile(user._1).map {
              p => {
                val newHubUser = HubUser(userName = user._1,
                                         firstName = p.firstName,
                                         lastName = p.lastName,
                                         email = p.email,
                                         userProfile = models.UserProfile(
                                           isPublic = p.isPublic,
                                           description = p.description,
                                           funFact = p.funFact,
                                           websites = p.websites,
                                           twitter = p.twitter,
                                           linkedIn = p.linkedIn
                                         )
                                        )
                HubUser.insert(newHubUser)
                HubUser.findByUsername(user._1)
              }
            }.getOrElse {
              ErrorReporter.reportError(request, "Could not create local HubUser for user %s".format(user._1), theme)
              return Action { implicit request => Redirect(controllers.routes.Authentication.login).flashing(("error", "Sorry, something went wrong while logging in, please try again")) }
            }
         }

          HubUser.findBookmarksCollection(user._1) match {
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
            "connectedUserId" -> u.get._id.toString,
            AccessControl.ORGANIZATIONS -> HubServices.userProfileService.listOrganizations(u.get.userName).mkString(","),
            AccessControl.GROUPS -> u.get.groups.mkString(","),
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