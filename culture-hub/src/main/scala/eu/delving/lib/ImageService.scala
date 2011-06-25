package eu.delving.lib

import net.liftweb.http.rest.RestHelper
import net.liftweb.common.Logger._
import net.liftweb.common.Logger
import net.liftweb.http.XmlResponse._
import net.liftweb.http.{XmlResponse}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ImageService extends RestHelper {

  val log = Logger("ImageService")

  serve {

    case request@"service" :: "image" :: Nil Get _ => {
      XmlResponse(<foo></foo>)

    }
  }


}