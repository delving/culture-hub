package controllers

import _root_.models.User
import play.mvc.Controller
import extensions.AdditionalActions

/**
 * Root controller for culture-hub. Takes care of checking URL parameters and other generic concerns.
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DelvingController extends Controller with AdditionalActions {

  def getUser(displayName: String): User = {
    User.find("displayName = {displayName}").on("displayName" -> displayName).first().getOrElse(User.nobody)
  }


}