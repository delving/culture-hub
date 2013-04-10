package controllers.ead

import controllers.DelvingController
import play.api.mvc._
import play.api._
import play.api.Play.current
import scala.xml._
import net.liftweb.json._
import collection.immutable.Stack
import collection.mutable.ArrayBuffer
import com.wordnik.swagger.annotations.{ ApiParam, ApiOperation, Api }
import util.EADSimplifier

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
@Api(value = "/experiments/ead", listingPath = "/api-docs.{format}/experiments/ead", description = "EAD prototype")
object Prototype extends DelvingController {

  val source = "apeEAD_SE_KrA_0058.xml"

  @ApiOperation(value = "Sample data")
  def sampleData = Action {
    implicit request =>

      Play.resourceAsStream(source) map {
        resourceStream =>
          {
            val source = Source.fromInputStream(resourceStream)
            val xml = scala.xml.XML.load(source)
            val s = util.Json.renderToJson(xml)
            Ok(s.toString)
          }
      } getOrElse {
        InternalServerError("Couldn't find test resource")
      }
  }

  @ApiOperation(value = "Sample view")
  def sampleView = Action {
    implicit request =>
      Ok(Template())
  }

  @ApiOperation(
    value = "Render a tree so that it can be displayed by FancyTree",
    notes = "Returns the JSON representation of the tree",
    responseClass = "tree",
    httpMethod = "GET"
  )
  def tree(@ApiParam(value = "Optional path for which to render a subtree") path: Option[String],
    @ApiParam(value = "Whether or not to limit the depth of the tree, for lazy loading (true by default)") limited: Boolean) = Action {
    implicit request =>

      Play.resourceAsStream(source) map {
        resourceStream =>
          {
            val source = Source.fromInputStream(resourceStream)
            val xml = scala.xml.XML.load(source)
            val simplified = EADSimplifier.simplify(xml)
            val json = util.Json.toJson(simplified)
            val unlimited = request.queryString.getFirst("limited").map(_ == "false").getOrElse(false)

            val transformed = transformTree(json, request.queryString.getFirst("path"), unlimited, transformSimplifiedTreeToFancyNode, renderAsArray = true, skipRoot = true)

            transformed map { t =>
              Ok(pretty(net.liftweb.json.render(t))).as(JSON)
            } getOrElse {
              NotFound
            }
          }
      } getOrElse {
        InternalServerError("Couldn't find test resource")
      }
  }

  @ApiOperation(
    value = "Render a source tree, eventually only rendering parts of it",
    notes = "Returns the JSON representation of the source document",
    responseClass = "sourceTree",
    httpMethod = "GET"
  )
  def sourceTree(@ApiParam(value = "Optional path for which to render a subtree") path: Option[String],
    @ApiParam(value = "Whether or not to limit the depth of the tree (true by default)") limited: Boolean) = Action {
    implicit request =>

      Play.resourceAsStream(source) map {
        resourceStream =>
          {
            val source = Source.fromInputStream(resourceStream)
            val xml = scala.xml.XML.load(source)
            val json = util.Json.toJson(xml)
            val unlimited = request.queryString.getFirst("limited").map(_ == "false").getOrElse(false)
            val transformed = transformTree(json, request.queryString.getFirst("path"), unlimited, transformSourceNode)

            transformed map { t =>
              Ok(pretty(net.liftweb.json.render(t))).as(JSON)
            } getOrElse {
              NotFound
            }
          }
      } getOrElse {
        InternalServerError("Couldn't find test resource")
      }
  }

  def simplifiedTree(@ApiParam(value = "Optional path for which to render a subtree") path: Option[String],
    @ApiParam(value = "Whether or not to limit the depth of the tree (true by default)") limited: Boolean) = Action {
    implicit request =>

      Play.resourceAsStream(source) map {
        resourceStream =>
          {
            val source = Source.fromInputStream(resourceStream)
            val xml = scala.xml.XML.load(source)
            val simplified = EADSimplifier.simplify(xml)
            val json = util.Json.toJson(simplified)
            val unlimited = request.queryString.getFirst("limited").map(_ == "false").getOrElse(false)
            val transformed = transformTree(json, request.queryString.getFirst("path"), unlimited, transformSourceNode)

            transformed map { t =>
              Ok(pretty(net.liftweb.json.render(t))).as(JSON)
            } getOrElse {
              NotFound
            }

          }
      } getOrElse {
        InternalServerError("Couldn't find test resource")
      }
  }

  def transformTree(json: JValue, key: Option[String], unlimited: Boolean = false,
    transformer: (String, JValue, Stack[String], Int, Option[String], ArrayBuffer[JValue], Int) => JValue,
    renderAsArray: Boolean = false,
    skipRoot: Boolean = false): Option[JValue] = {
    json match {
      case o @ JObject(fields: Seq[JField]) =>
        val root = fields.head
        val subtree = ArrayBuffer[JValue]()
        val transformed = transformer(root.name, root.value, Stack(), 0, key, subtree, if (unlimited) -1 else 1) match {
          case JObject(fields: Seq[JField]) if skipRoot => List(JObject(fields))
          case o @ JObject(fields: Seq[JField]) => List(JObject(List(JField(root.name, o))))
        }

        if (key.isDefined && subtree.isEmpty && key.get != "/") {
          None
        } else {
          Some(
            if (renderAsArray) {
              JArray(
                if (key.isDefined && !subtree.isEmpty) subtree.toList else transformed
              )
            } else {
              if (key.isDefined && !subtree.isEmpty) subtree.head else transformed.head
            }
          )
        }

      case other @ _ => throw new RuntimeException("Huh? Unknown node type: " + other)
    }
  }

  def transformSimplifiedTreeToFancyNode(
    title: String,
    value: JValue,
    path: Stack[String],
    depth: Int,
    key: Option[String],
    subtree: ArrayBuffer[JValue],
    depthLimit: Int = 1): JValue = {

    def node(value: JObject) = {
      val title = (value \ "title") match { case JString(t) => t }
      val path = (value \ "key") match { case JString(p) => p }
      val nodes = (value \ "node") match { case JArray(c) => c; case _ => List.empty }
      (title, path, nodes)
    }

    value match {
      case o @ JObject(fields: Seq[JField]) =>
        val (title, p, nodes) = node(o)
        JObject(List(
          JField("title", JString(title)),
          JField("folder", JBool(!nodes.isEmpty)),
          JField("data", JObject(List(JField("path", JString(p))))),
          JField("key", JString(path.reverse.mkString + "/" + title)),
          JField("children", JArray(nodes.zipWithIndex.map { c =>
            transformSimplifiedTreeToFancyNode(s"node[${c._2}]", c._1, path push s"/node[${c._2}]", depth + 1, key, subtree, depthLimit)
          }))
        ))

      case JString(s) =>
        JObject(List(
          JField("title", JString(s)),
          JField("folder", JBool(false))
        ))
    }

  }

  def transformFancyNode(title: String,
    value: JValue,
    path: Stack[String],
    depth: Int,
    key: Option[String],
    subtree: ArrayBuffer[JValue],
    depthLimit: Int = 1): JValue = {

    val pathMatched = key != None && key.get == (path.reverse.mkString + "/" + title)

    val v = value match {
      case JObject(fields: Seq[JField]) =>
        val baseFields = List(
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
            value = JString(path.reverse.mkString + "/" + title)
          )
        )
        val renderedFields = if ((depth >= depthLimit && depthLimit > -1) && (key.isEmpty || (key.isDefined && !subtree.isEmpty))) {
          baseFields ::: List(
            JField(
              name = "lazy",
              value = JBool(value = true)
            )
          )

        } else baseFields ::: List(
          JField(
            name = "children",
            value = JArray(
              fields map { field =>
                val node = transformFancyNode(field.name, field.value, path push ("/" + title), depth + 1, key, subtree, depthLimit)
                node
              }
            )
          )
        )
        JObject(renderedFields)
      case JArray(values: Seq[JValue]) =>
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
            name = "children",
            value = JArray(values.zipWithIndex.map { v =>
              val node = transformFancyNode(title, v._1, path push (s"/$title[${v._2}]"), depth + 1, key, subtree, depthLimit)
              node
            })
          )))

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
          value = JArray(
            List(JObject(List(
              JField(
                name = "title",
                value = JString(s)
              )
            ))))
        )
      ))
      case other @ _ => throw new RuntimeException("Unknown node type: " + other)
    }
    if (pathMatched) subtree append v
    v
  }

  def transformSourceNode(
    title: String,
    value: JValue,
    path: Stack[String],
    depth: Int, key: Option[String],
    subtree: ArrayBuffer[JValue],
    depthLimit: Int = 1): JValue = {
    val pathMatched = key != None && key.get == (path.reverse.mkString + "/" + title)
    val v = value match {
      case JObject(fields: Seq[JField]) =>
        JObject(fields.map { field =>
          JField(field.name, transformSourceNode(field.name, field.value, path push ("/" + title), depth + 1, key, subtree, depthLimit))
        })
      case JArray(values: Seq[JValue]) =>
        JArray(values.zipWithIndex.map { v =>
          {
            // we push the title again to generate coherent paths, e.g. /some/array, /some/array/array[0], etc.
            val updatedPath = path push (s"/$title")
            transformSourceNode(s"$title[${v._2}]", v._1, updatedPath, depth + 1, key, subtree, depthLimit)
          }
        })
      case other @ _ => other
    }
    if (pathMatched) subtree append v
    v
  }
}