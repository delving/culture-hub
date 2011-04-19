package bootstrap.liftweb

import net.liftweb.http.rest.RestHelper

/**
 * todo: javadoc
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */


object DelvingServices extends RestHelper {
  serve {
    case "service" :: Nil XmlGet _ => service()
  }

  private def service() =
    <service>
      <ladies-and-gentlemen>
         <the-delving-dispatcher/>
      </ladies-and-gentlemen>
    </service>

}