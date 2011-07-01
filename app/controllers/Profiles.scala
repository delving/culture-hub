package controllers

import models.User
import play.mvc.{Controller}
import net.liftweb.json.Serialization.{read, write}
import play.classloading.enhancers.LocalvariablesNamesEnhancer
import java.lang.reflect.Constructor
import net.liftweb.json.{Extraction, DefaultFormats, ParameterNameReader}
import extensions.AdditionalActions

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Profiles extends DelvingController {

  import views.Profile._

  def index(): AnyRef = {
    val displayName = params.get("user")
    val u: User = getUser(displayName)
    RenderMultitype(html.index, ('user, u))
  }
}
