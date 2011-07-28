package controllers

import extensions.AdditionalActions
import java.io.File
import play.Play
import com.mongodb.casbah.commons.MongoDBObject
import models.{PortalTheme, User}
import util.LocalizedFieldNames
import scala.collection.JavaConversions._
import cake.ComponentRegistry
import play.mvc._

/**
 * Root controller for culture-hub. Takes care of checking URL parameters and other generic concerns.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait DelvingController extends Controller with AdditionalActions with FormatResolver with ParameterCheck with ThemeAware with UserAuthentication {

  @Before def setConnectedUser() {

    val user = User.findOne(MongoDBObject("reference.username" -> connectedUser, "isActive" -> true))
    user map {
      u => {
        renderArgs.put("fullName", u.fullname)
        renderArgs.put("displayName", u.reference.username)
      }
    }
  }

  // TODO
  @Util def getNode = "cultureHub"

  @Util def getUserId(username: String): String = username + "#" + getNode

  @Util def getUser(displayName: String): User = {
    User.findOne(MongoDBObject("reference.id" -> getUserId(displayName), "isActive" -> true)).getOrElse(User.nobody)
  }

  /**
   * Gets a path from the file system, based on configuration key. If the key or path is not found, an exception is thrown.
   */
  @Util def getPath(key: String): File = {
    val imageStorePath = Option(Play.configuration.get(key)).getOrElse(throw new RuntimeException("You need to configure %s in conf/application.conf" format (key))).asInstanceOf[String]
    val imageStore = new File(imageStorePath)
    if (!imageStore.exists()) {
      throw new RuntimeException("Could not find path %s for key %s" format (imageStore.getAbsolutePath, key))
    }
    imageStore
  }

}


trait FormatResolver {
  self: Controller =>

  // supported formats, based on the formats automatically inferred by Play and the ones we additionally support in the format parameter
  val supportedFormats = List("html", "xml", "json", "kml")

  @Before(priority = 1)
  def setFormat(): AnyRef = {
    if (request.headers.get("accept").value().equals("application/vnd.google-earth.kml+xml")) {
      request.format = "kml";
    }
    val formatParam = Option(params.get("format"))
    if (formatParam.isDefined && supportedFormats.contains(formatParam.get)) {
      request.format = params.get("format")
    } else if (formatParam.isDefined && !supportedFormats.contains(formatParam.get)) {
      return Error("Unsupported format %s" format (formatParam.get))
    }
    Continue
  }
}

/**
 * Checks the validity of parameters
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Gerald de Jong <geralddejong@gmail.com>
 */
trait ParameterCheck {
  self: Controller =>

  @Before(priority = 0)
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

@Util object Allowed {
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
    "image", "fcgi-bin", "upload", "admin", "registration", "users")

}

trait ThemeAware {

  val themeHandler = ComponentRegistry.themeHandler
  val localizedFieldNames = new LocalizedFieldNames

  private val themeThreadLocal: ThreadLocal[PortalTheme] = new ThreadLocal[PortalTheme]
  private val lookupThreadLocal: ThreadLocal[LocalizedFieldNames.Lookup] = new ThreadLocal[LocalizedFieldNames.Lookup]

  implicit def theme = themeThreadLocal.get()

  implicit def lookup = lookupThreadLocal.get()

  @Before(priority = 2)
  def setTheme() {
    val portalTheme = themeHandler.getByRequest(Http.Request.current())
    themeThreadLocal.set(portalTheme)
    lookupThreadLocal.set(localizedFieldNames.createLookup(portalTheme.localiseQueryKeys))
  }

  @After
  def cleanup() {
    themeThreadLocal.remove()
    lookupThreadLocal.remove()
  }

}