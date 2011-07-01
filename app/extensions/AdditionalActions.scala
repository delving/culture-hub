package extensions

import play.classloading.enhancers.LocalvariablesNamesEnhancer
import play.mvc.Http.{Response, Request}
import net.liftweb.json.Serialization._
import net.liftweb.json.{Extraction, DefaultFormats, ParameterNameReader}
import java.lang.reflect.{Method, Constructor}
import play.templates.Html
import play.mvc.results.{RenderHtml, RenderXml, RenderJson, Result}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

// glue for lift-json
object PlayParameterNameReader extends ParameterNameReader {
  def lookupParameterNames(constructor: Constructor[_]) = {
    Set(LocalvariablesNamesEnhancer.lookupParameterNames(constructor).toArray(new Array[String](1)): _*)
  }
}

/**
 * This trait provides additional actions that can be used in controllers
 */
trait AdditionalActions {

  def RenderMultitype(template: play.templates.BaseScalaTemplate[play.templates.Html, play.templates.Format[play.templates.Html]], args: (Symbol, Any)*) = new RenderMultitype(template, args: _*)

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
      new RenderXml(<response>
        {net.liftweb.json.Xml.toXml(Extraction.decompose(arg))}
      </response>)(request, response)
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