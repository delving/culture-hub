package controllers

import _root_.models.User
import extensions.AdditionalActions
import play.mvc.{Before, Controller}

/**
 * Root controller for culture-hub. Takes care of checking URL parameters and other generic concerns.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

trait DelvingController extends Controller with AdditionalActions {

  @Before
  def checkParameters(): AnyRef = {
    val parametersToCheck = List("user", "collection", "label", "dobject", "story")
    parametersToCheck map {
      paramName => Option(params.get(paramName)) map {
        paramValue => Allowed(paramValue) match {
          case None => return Error("""Forbidden value "%s" for parameter "%s"""" format (paramValue, paramName))
          case _ => Continue
        }
      }
    }
    Continue
  }

  object Allowed {
    def apply(string: String): Option[String] = {
      FORBIDDEN map {
        f => if (string.contains(f)) {
          return None
        }
      }
      Some(string)
    }
  }

  val FORBIDDEN = Set(
    "object", "profile", "map", "graph", "label", "collection",
    "story", "user", "service", "services", "portal", "api", "index",
    "add", "edit", "save", "delete", "update", "create", "search",
    "image", "fcgi-bin", "upload")


  def getUser(displayName: String): User = {
    User.find("displayName = {displayName}").on("displayName" -> displayName).first().getOrElse(User.nobody)
  }
}

object DelvingController {

}