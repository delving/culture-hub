package bootstrap.liftweb

import net.liftweb.http.rest.RestHelper
import eu.delving.model.User
import _root_.net.liftweb.common._
import _root_.net.liftweb.http._

/**
 * todo: javadoc
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */


object DelvingServices extends RestHelper {
  serve {
    case "service" :: Nil XmlGet _ => service("open")
    case "protected-service" :: Nil XmlGet _ => if (User.notLoggedIn_?) Full(ForbiddenResponse("fuck off!")) else service("protected!")
// todo: figure out why this url is not protected by the SiteMap
//    case "protected-service" :: Nil XmlGet _ => service("protected!")
  }

  private def service(say : String) =
    <service>
      <ladies-and-gentlemen>
         <the-delving-dispatcher status={say}/>
      </ladies-and-gentlemen>
    </service>

}