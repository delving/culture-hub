package extensions

import play.mvc.Http.{Response, Request}
import models.User
import play.mvc.Controller
import play.mvc.results._

/**
 * This trait provides additional actions that can be used in controllers
 */
trait AdditionalActions extends Extensions {
  self: Controller =>

  def JsonBadRequest(data: AnyRef): Result = {
    response.status = 400
    Json(data)
  }

  def RenderKml(entity: AnyRef) = new RenderKml(entity)

  def TextError(why: String, status: Int = 500) = {
    response.status = new java.lang.Integer(status)
    Text(why)
  }
}

class RenderKml(entity: AnyRef) extends Result {
  def apply(request: Request, response: Response) {
    val doc = entity match {
      case u: User => KMLSerializer.toKml(Option(u))
      case _ => KMLSerializer.toKml(None)
    }
    new RenderXml(doc.toString())(request, response)
  }
}