package controllers.ead

import controllers.DelvingController
import play.api.mvc._
import play.api._
import play.api.Play.current
import scala.xml._
import net.liftweb.json._
import collection.immutable.Stack

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object Prototype extends DelvingController {

  def sampleData = Action {
    implicit request =>

      Play.resourceAsStream("mildred_davenport.xml") map {
        resourceStream =>
          {
            val source = Source.fromInputStream(resourceStream)
            val xml = scala.xml.XML.load(source)
            Ok(util.Json.toJson(xml))
          }
      } getOrElse {
        InternalServerError("Couldn't find test resource")
      }
  }

  def sampleView = Action {
    implicit request =>
      Ok(Template())
  }

  def dynatreeSample = Action {
    implicit request =>

      Play.resourceAsStream("mildred_davenport.xml") map {
        resourceStream =>
          {
            val source = Source.fromInputStream(resourceStream)
            val xml = scala.xml.XML.load(source)
            val json = Xml.toJson(xml)
            val transformed = transformToDynatree(json)

            Ok(pretty(net.liftweb.json.render(transformed))).as(JSON)
          }
      } getOrElse {
        InternalServerError("Couldn't find test resource")
      }

  }

  def transformToDynatree(json: JValue): JValue = {
    json match {
      case o @ JObject(fields: Seq[JField]) =>
        val root = fields.head
        JArray(
          transformNode(root.name, root.value, Stack(), 0) match {
            case JObject(fields: Seq[JField]) => List(JObject(fields))
          }
        )
    }

  }

  def transformNode(title: String, value: JValue, path: Stack[String], depth: Int): JValue = value match {
    case JObject(fields: Seq[JField]) =>
      JObject(List(
        JField(
          name = "title",
          value = JString(title)
        ),
        JField(
          name = "folder",
          value = JBool(value = true)
        ),
        JField(
          name = "key",
          value = JString(path.reverse.mkString)
        ),
        JField(
          name = "children",
          value = JArray(
            fields map { field =>
              val node = transformNode(field.name, field.value, path push ("/" + title), depth + 1)
              node
            }
          )
        )
      ))
    case JArray(values: Seq[JValue]) =>
      JArray(values.zipWithIndex.map { v =>
        val node = transformNode(title, v._1, path push (s"/$title[${v._2}]"), depth)
        node
      })
    case JString(s) => JObject(List(
      JField(
        name = "title",
        value = JString(title)
      ),
      JField(
        name = "folder",
        value = JBool(value = true)
      ),
      JField(
        name = "children",
        value = JArray(List(JObject(List(
          JField(
            name = "title",
            value = JString(s)
          )
        ))))
      )

    ))
  }

}
