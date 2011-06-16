package eu.delving.lib

import eu.delving.model._
import net.liftweb.http.rest.{JsonXmlAble, RestHelper}
import net.liftweb.common._
import net.liftweb.http.Req

/**
 * Dispatch the services
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */


object BrowseService extends RestHelper {

  val log = Logger("BrowseService")

  def acceptsHtml(in: Req) = in.weightedAccept.find(_.matches("text" -> "html")).isDefined

  override protected def jsonResponse_?(in: Req): Boolean = (in.acceptsJson_? && !acceptsHtml(in)) || in.param("format") == "json"
  override protected def xmlResponse_?(in: Req): Boolean = (in.acceptsXml_? && !acceptsHtml(in)) || in.param("format") == "xml"

  case class Response(response: AnyRef) extends JsonXmlAble

  def give(any : AnyRef) = Full(Response(any))

  serveJx[Response] {
    case "service" :: "user" :: Nil Get _ => give(User.findAll.head.getValue)
  }

}