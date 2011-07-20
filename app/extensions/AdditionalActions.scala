package extensions

import play.classloading.enhancers.LocalvariablesNamesEnhancer
import play.mvc.Http.{Response, Request}
import net.liftweb.json.Serialization._
import java.lang.reflect.{Method, Constructor}
import play.templates.Html
import play.mvc.results.{RenderHtml, RenderXml, RenderJson, Result}
import models.User
import org.bson.types.ObjectId
import net.liftweb.json._
import play.mvc.Http
import play.exceptions.UnexpectedException
import net.liftweb.json

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

  def Json(data: AnyRef) = new RenderLiftJson(data)

  def RenderMultitype(template: play.templates.BaseScalaTemplate[play.templates.Html, play.templates.Format[play.templates.Html]], args: (Symbol, Any)*) = new RenderMultitype(template, args: _*)

  def RenderKml(entity: AnyRef) = new RenderKml(entity)

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


class RenderLiftJson(data: AnyRef, status: java.lang.Integer = 200) extends Result {
  def apply(request: Request, response: Response) {
    implicit val formats = new DefaultFormats {
      override val parameterNameReader = PlayParameterNameReader
    } + new ObjectIdSerializer
    response.status = status
    new RenderJson(write(data))(request, response)
  }
}

class ObjectIdSerializer extends Serializer[ObjectId] {
  private val ObjectIdClass = classOf[ObjectId]

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), ObjectId] = {
    case (TypeInfo(ObjectIdClass, _), json) => json match {
      case JObject(JField("$oid", JString(s)) :: Nil) if (ObjectId.isValid(s)) =>
        new ObjectId(s)
      case x => throw new MappingException("Can't convert " + x + " to ObjectId")
    }
  }

  def serialize(implicit formats: Formats): PartialFunction[Any, JValue] = {
    case x: ObjectId => objectIdAsJValue(x)
  }

  def objectIdAsJValue(oid: ObjectId): JValue = JObject(JField("$oid", JString(oid.toString)) :: Nil)
}

class RenderText(text: String, status: java.lang.Integer = 200) extends Result {
  def apply(request: Http.Request, response: Http.Response) {
    try {
      setContentTypeIfNotSet(response, "text/plain; charset=" + response.encoding)
      response.status = status
      response.out.write(text.getBytes(getEncoding))
    }
    catch {
      case e: Exception => {
        throw new UnexpectedException(e)
      }
    }
  }
}

class RenderJson(body: String, status: java.lang.Integer = 200) extends Result {
  def apply(request: Request, response: Response) {
    try {
      val encoding = getEncoding
      setContentTypeIfNotSet(response, "application/json; charset=" + encoding);
      response.status = status
      response.out.write(body.getBytes(encoding));
    } catch {
      case e: Exception => throw new UnexpectedException(e);
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
