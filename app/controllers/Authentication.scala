package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.libs.Crypto
import play.libs.Time
import play.api.i18n.Messages
import extensions.MissingLibs
import models.HubUser
import core.{Constants, HubServices}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Authentication extends ApplicationController {

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

  def login = ApplicationAction {
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
  def authenticate: Action[AnyContent] = ApplicationAction {
    Action { implicit request =>
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


// FIXME re-introduce this later
//          HubUser.findBookmarksCollection(user._1) match {
//            case None =>
//              // create default bookmarks collection
//              val bookmarksCollection = UserCollection(
//                TS_update = new Date(),
//                userName = user._1,
//                name = "Bookmarks",
//                description = "Bookmarks",
//                visibility = Visibility.PRIVATE,
//                thumbnail_id = None,
//                thumbnail_url = None,
//                isBookmarksCollection = Some(true))
//              val userCollectionId = UserCollection.insert(bookmarksCollection)
//              try {
//                // IndexingService.index(bookmarksCollection.copy(_id = userCollectionId.get))
//              } catch {
//                case t => ErrorReporter.reportError(this.getClass.getName, t, "Could not index Bookmarks collection %s for newly created user %s".format(userCollectionId.get.toString), theme)
//              }
//            case Some(bookmarks) => // it's ok
//          }

          val action = (request.session.get("uri") match {
            case Some(uri) => Redirect(uri)
            case None => Redirect(controllers.routes.Application.index)
          }).withSession(
            Constants.USERNAME -> user._1,
            "connectedUserId" -> u.get._id.toString,
            Constants.ORGANIZATIONS -> u.get.organizations.mkString(","),
            Constants.GROUPS -> u.get.groups.mkString(","),
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