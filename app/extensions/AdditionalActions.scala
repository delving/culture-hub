package extensions

import play.classloading.enhancers.LocalvariablesNamesEnhancer
import play.mvc.Http.{Response, Request}
import java.lang.reflect.{Method, Constructor}
import play.templates.Html
import play.mvc.results.{RenderHtml, RenderXml, RenderJson, Result}
import models.User
import net.liftweb.json
import json._
import json.Serialization._
import com.codahale.jerkson.util.CaseClassSigParser
import play.mvc.Controller
import org.codehaus.jackson.map._
import org.codehaus.jackson.map.Module.SetupContext
import org.codehaus.jackson.Version

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

// glue for lift-json, still used in some places
object PlayParameterNameReader extends ParameterNameReader {

  import scala.collection.JavaConversions._

  def lookupParameterNames(constructor: Constructor[_]) = LocalvariablesNamesEnhancer.lookupParameterNames(constructor)
}


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

  def Json(data: AnyRef): Result = new RenderJson(CHJson.generate(data))

  def RenderMultitype(template: play.templates.BaseScalaTemplate[play.templates.Html, play.templates.Format[play.templates.Html]], args: (Symbol, Any)*) = new RenderMultitype(template, args: _*)

  def RenderKml(entity: AnyRef) = new RenderKml(entity)

  def TextError(why: String, status: Int = 500) = {
    response.status = new java.lang.Integer(status)
    Text(why)
  }
}


/**
 * Experimental action to generically render an object as json, xml or html depending on the requested format.
 */
class RenderMultitype(template: play.templates.BaseScalaTemplate[play.templates.Html, play.templates.Format[play.templates.Html]], args: (Symbol, Any)*) extends Result {

  def apply(request: Request, response: Response) {

    implicit val formats = new DefaultFormats {
      override val parameterNameReader = PlayParameterNameReader
    }

    // TODO we for the moment only handle a single parameter. We have to see how multi-parameter JSON and XML responses would look like
    val arg = args(0)._2.asInstanceOf[AnyRef]

    if (request.format == "json") {
      new RenderJson(write(arg))(request, response)
    } else if (request.format == "xml") {
      // TODO for now we still have lift-json here because we want to use the XML extraction
      // but maybe there is another way to achieve this
      val doc = <response>
        {net.liftweb.json.Xml.toXml(Extraction.decompose(arg))}
      </response>
      new RenderXml(doc.toString())(request, response)
    } else if (request.format == "kml") {
      new RenderKml(arg)(request, response)
    } else {
      // TODO this was hacked together in five minutes. Due to lack of knowledge of the scala reflection mechnism I resolved to the ugly code below
      // but I guess there is a better way
      val c = Class.forName(template.getClass.getName)
      val templateObject: AnyRef = c.getField("MODULE$").get(null).asInstanceOf[template.type]
      val method: Method = templateObject.getClass.getMethod("apply", arg.asInstanceOf[AnyRef].getClass)
      val invoked: Html = method.invoke(template, arg.asInstanceOf[AnyRef]).asInstanceOf[Html]

      new RenderHtml(invoked.toString())(request, response)
    }
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
