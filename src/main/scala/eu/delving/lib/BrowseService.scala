package eu.delving.lib

import net.liftweb.common._
import eu.delving.model._
import net.liftweb.http.rest.{JsonXmlAble, RestHelper}

/**
 * Dispatch the services
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */


object BrowseService extends RestHelper {

  val log = Logger("BrowseService")

  override protected def defaultGetAsJson: Boolean = false

  case class Response(response: AnyRef) extends JsonXmlAble

  serveJx[Response] {
    case "service" :: "user" :: Nil Get _ if User.notLoggedIn_? => Full(Response(User.findAll.head.getPublic))
    case "service" :: "user" :: Nil Get _ if User.loggedIn_? => Full(Response(User.findAll.head.getPrivate))
  }

}