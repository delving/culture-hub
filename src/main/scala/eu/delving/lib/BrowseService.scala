package eu.delving.lib

import net.liftweb.http.rest.RestHelper
import net.liftweb.common._
import eu.delving.model.{UserCase, User}

/**
 * Dispatch the services
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */


object BrowseService extends RestHelper {

  val log = Logger("BrowseService")

  serveJx[UserCase] {
    case "service" :: "user" :: Nil Get _ => Full(User.findAll.head.getCase)
  }

}