package controllers

import models.User
import play.mvc.{Controller}
import net.liftweb.json.Serialization.{read, write}
import play.classloading.enhancers.LocalvariablesNamesEnhancer
import java.lang.reflect.Constructor
import net.liftweb.json.{Extraction, DefaultFormats, ParameterNameReader}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

// glue for lift-json
object PlayParameterNameReader extends ParameterNameReader {
  def lookupParameterNames(constructor: Constructor[_]) =  {
    Set(LocalvariablesNamesEnhancer.lookupParameterNames(constructor).toArray(new Array[String](1)) : _*)
  }
}

object Profiles extends Controller {

  import views.Profile._

  implicit val formats = new DefaultFormats {
    override val parameterNameReader = PlayParameterNameReader
  }


  def index(): AnyRef = {

    val displayName = params.get("user")

    val user: User = User.find("displayName = {displayName}").on("displayName" -> displayName).first().getOrElse(User.nobody)

    if (request.format == "json") {
      return Json(write(user))
    }
    if(request.format == "xml") {
      return Xml(<response>{net.liftweb.json.Xml.toXml(Extraction.decompose(user))}</response>)
    }

    html.index(user = user)
  }
}
