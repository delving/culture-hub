package controllers

import extensions.AdditionalActions
import java.io.File
import play.Play
import com.mongodb.casbah.commons.MongoDBObject
import scala.collection.JavaConversions._
import cake.ComponentRegistry
import play.mvc._
import results.Result
import models._
import org.bson.types.ObjectId
import play.data.validation.Validation
import util.{ProgrammerException, LocalizedFieldNames}

/**
 * Root controller for culture-hub. Takes care of checking URL parameters and other generic concerns.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait DelvingController extends Controller with ModelImplicits with AdditionalActions with FormatResolver with ParameterCheck with ThemeAware with UserAuthentication {

  // ~~~ user variables handling for view rendering (connected and browsed)

  @Before(priority = 0) def setConnectedUser() {
    val user = User.findOne(MongoDBObject("reference.username" -> connectedUser, "isActive" -> true))
    user foreach {
      u => {
        renderArgs += ("fullName", u.fullname)
        renderArgs += ("userName", u.reference.username)
        renderArgs += ("userId", u._id)
      }
    }
  }

  @Before(priority = 0) def setBrowsedUser() {
    Option(params.get("user")) foreach { userName =>
      val user = User.findOne(MongoDBObject("reference.username" -> userName, "reference.node" -> getNode, "isActive" -> true))
      user match {
        case Some(u) =>
          renderArgs += ("browsedFullName", u.fullname)
          renderArgs += ("browsedUserId", u._id)
          renderArgs += ("browsedUserName", u.reference.username)
        case None =>
          renderArgs += ("browsedUserNotFound", userName)
      }
    }
  }

  @Before def brokenPlayBindingWorkaround(page: Int = 1) {
    val page = params.get("page", classOf[Int])
    if(page == 0) {
      params.remove("page")
      params.put("page", "1")
    }
  }

  @Before def setViewUtils() {
    renderArgs += ("viewUtils", new ViewUtils(theme))
  }

  @Util def viewUtils: ViewUtils = renderArgs.get("viewUtils").asInstanceOf[ViewUtils]

  @Util def connectedUserId = renderArgs.get("userId", classOf[ObjectId])

  @Before(priority = 1) def checkBrowsedUser(): Result = {
    if(!browsedUserExists) return NotFound("User %s was not found".format(renderArgs.get("browsedUserNotFound", classOf[String])))
    Continue
  }

  // ~~~ convenience methods to access user information

  // TODO
  @Util def getNode = "cultureHub"

  @Util def getUserId(username: String): String = username + "#" + getNode

  @Util def getUser(userName: String): Either[Result, User] = User.findOne(MongoDBObject("reference.id" -> getUserId(userName), "isActive" -> true)) match {
    case Some(user) => Right(user)
    case None => Left(NotFound("Could not find user " + userName))
  }

  @Util def browsedUserName: String = renderArgs.get("browsedUserName", classOf[String])

  @Util def browsedUserId: ObjectId = renderArgs.get("browsedUserId", classOf[ObjectId])

  @Util def browsedFullName: String = renderArgs.get("browsedFullName", classOf[String])

  @Util def browsedUserExists: Boolean = renderArgs.get("browsedUserNotFound") == null

  @Util def browsedIsConnected: Boolean = browsedUserId == connectedUserId

  @Util def browsingUser: Boolean = browsedUserName != null

  // ~~~ convenience methods

  @Util def listPageTitle(itemName: String) = if(browsingUser) "List of %s for user %s".format(extensions.ViewExtensions.pluralize(itemName), browsedUserName) + browsedFullName else "List of " + extensions.ViewExtensions.pluralize(itemName)

  /**
   * Gets a path from the file system, based on configuration key. If the key or path is not found, an exception is thrown.
   */
  @Util def getPath(key: String, create: Boolean = false): File = {
    val path = Option(Play.configuration.get(key)).getOrElse(throw new RuntimeException("You need to configure %s in conf/application.conf" format (key))).asInstanceOf[String]
    val store = new File(path)
    if (!store.exists() && create) {
      store.mkdirs()
    } else if(!store.exists()) {
      throw new RuntimeException("Could not find path %s for key %s" format (store.getAbsolutePath, key))
    }
    store
  }

  @Util def findThumbnailCandidate(files: Seq[StoredFile]): Option[StoredFile] = {
    for(file <- files) if(file.contentType.contains("image")) return Some(file)
    None
  }

  @Util def validate(viewModel: AnyRef): Option[Map[String, String]] = {
    import scala.collection.JavaConversions.asScalaIterable

    if(!Validation.valid("object", viewModel).ok) {
      val fieldErrors = asScalaIterable(Validation.errors).filter(_.getKey.contains(".")).map { error => (error.getKey.split("\\.")(1), error.message()) }
      val globalErrors = asScalaIterable(Validation.errors).filterNot(_.getKey.contains(".")).map { error => ("global", error.message()) }
      val errors = fieldErrors ++ globalErrors
      Some(errors.toMap)
    } else {
      None
    }
  }


}


trait FormatResolver {
  self: Controller =>

  // supported formats, based on the formats automatically inferred by Play and the ones we additionally support in the format parameter
  val supportedFormats = List("html", "xml", "json", "kml", "token")

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

trait ThemeAware { self: Controller =>

  val themeHandler = ComponentRegistry.themeHandler
  val localizedFieldNames = new LocalizedFieldNames

  private val themeThreadLocal: ThreadLocal[PortalTheme] = new ThreadLocal[PortalTheme]
  private val lookupThreadLocal: ThreadLocal[LocalizedFieldNames.Lookup] = new ThreadLocal[LocalizedFieldNames.Lookup]

  implicit def theme = themeThreadLocal.get()

  implicit def lookup = lookupThreadLocal.get()

  @Before(priority = 0)
  def setTheme() {
    val portalTheme = themeHandler.getByRequest(Http.Request.current())
    themeThreadLocal.set(portalTheme)
    lookupThreadLocal.set(localizedFieldNames.createLookup(portalTheme.localiseQueryKeys))
    renderArgs.put("theme", theme)
  }

  @Finally
  def cleanup() {
    themeThreadLocal.remove()
    lookupThreadLocal.remove()
  }

}

/**
 * This class will hold all sort of utility methods that need to be called form the templates. It is meant to be initalized at each request
 * and be passed to the view using the renderArgs.
 *
 * It should replace the old views.context package object
 */
class ViewUtils(theme: PortalTheme) {

  def themeProperty[T](property: String, clazz: Class[T] = classOf[String])(implicit mf: Manifest[T]): T = {
    val key = "themes.%s.%s".format(theme.name, property)
    val value = Option(Play.configuration.getProperty(key)) match {
      case Some(prop) => prop
      case None =>
        Option(Play.configuration.getProperty("themes.default.%s".format(property))) match {
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

}