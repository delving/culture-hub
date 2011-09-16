package extensions

import play.mvc.Http.{Response, Request}
import models.User
import com.codahale.jerkson.util.CaseClassSigParser
import play.mvc.Controller
import org.codehaus.jackson.map._
import org.codehaus.jackson.map.Module.SetupContext
import org.codehaus.jackson.Version
import play.mvc.results._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object CHJson extends com.codahale.jerkson.Json {
  // this is where we setup out Jackson module for custom de/serialization
  val module: Module = new Module() {
    def getModuleName = "Delving"
    def version() = Version.unknownVersion()
    def setupModule(ctx: SetupContext) {
      ctx.addDeserializers(new AdditionalScalaDeserializers)
      ctx.addSerializers(new AdditionalScalaSerializers)
    }
  }
  mapper.registerModule(module)
}

/**
 * This trait provides additional actions that can be used in controllers
 */
trait AdditionalActions {
  self: Controller =>

  // this is where we set our classLoader for jerkson
  CaseClassSigParser.setClassLoader(play.Play.classloader)

  def Json(data: AnyRef): Result = new Result() {
    def apply(request: Request, response: Response) {
      val encoding = getEncoding
      setContentTypeIfNotSet(response, "application/json; charset=" + encoding)
      response.out.write(CHJson.generate(data).getBytes(encoding))
    }
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