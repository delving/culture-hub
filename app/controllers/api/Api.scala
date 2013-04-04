package controllers.api

import controllers._
import play.api.mvc._
import extensions.JJson
import core.{ HubModule, ExplainItem }
import com.escalatesoft.subcut.inject.BindingModule

/**
 * The API documentation
 *
 * TODO document all APIs
 * TODO remove or re-think this stuff
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class Api(implicit val bindingModule: BindingModule) extends DelvingController with RenderingExtensions {

  def ui = Action {
    implicit request =>
      Ok(Template)
  }

  def explanations(path: String): Action[AnyContent] = {
    val pathList = path.split("/").drop(1).toList
    if (pathList.isEmpty) {
      api
    } else {
      val explanation = pathList(0) match {
        case "proxy" => controllers.api.Proxy.explain(pathList.drop(1))
        case "index" => new controllers.api.Index()(HubModule).explain(pathList.drop(1))
        case _ => return noDocumentation(path)
      }
      explanation match {
        case Some(e) => renderExplanation(e)
        case None => noDocumentation(path)
      }
    }
  }

  /**
   * Index of APIs. The idea is not to explain each of them in detail, the root path of each should do that instead
   */
  def api = Root {
    Action {
      implicit request =>

        val apiDescription = "This is the list of all APIs offered by the CultureHub."

        val globalParams = List(
          ExplainItem("explain", List("true", "false"), "Providers an explanation of the API at the current path"),
          ExplainItem("format", List("xml", "json"), "Forces a specific output format. By default the HTTP Accept header is used")
        )

        val apis = List(
          ApiItem("search", "Search API", "search?explain=true"),
          ApiItem("search/provider", "Search API by provider", "search/provider/delving?query=test"),
          ApiItem("search/dataProvider", "Search API by dataProvider", "search/dataProvider/delving?query=test"),
          ApiItem("search/collection", "Search API by collection", "search/collection/delving?query=test"),
          ApiItem("oai-pmh", "OAI-PMH access point", "oai-pmh?verb=Identify"),
          ApiItem("proxy", "Search proxy"),
          ApiItem("providers", "Providers list"),
          ApiItem("dataProviders", "Data Providers list"),
          ApiItem("collections", "Collections list")
        )

        if (wantsXml) {
          val xml = <explain>
                      <description>{ apiDescription }</description>
                      <global-parameters>{ globalParams.map(_.toXml) }</global-parameters>
                      <api-list>{ apis.map(_.toXml) }</api-list>
                    </explain>
          Ok(xml)
        } else {
          val json = Map(
            "description" -> apiDescription,
            "global-parameters" -> globalParams.map(_.toJson),
            "api-list" -> apis.map(_.toJson)
          )
          Ok(JJson.generate(json)).as(JSON)
        }
    }
  }

  def explain = Action {
    implicit request => explainPath(request.path)(request)
  }

  /** routes to the appropriate explain response **/
  def explainPath(path: String): Action[AnyContent] = {
    val apiPath = path.substring("/api".length)
    explanations(apiPath)
  }

  def noDocumentation(path: String) = Action {
    implicit request =>
      val sorry = "Sorry, no documentation found for path " + path
      if (wantsXml) {
        Ok(<explain>
             <error>{ sorry }</error>
           </explain>)
      } else {
        Ok(JJson.generate(Map("error" -> sorry))).as(JSON)
      }
  }

  private def renderExplanation(explanation: Description) = Action {
    implicit request =>
      if (wantsXml) {
        Ok(explanation.toXml)
      } else {
        Ok(JJson.generate(explanation.toJson)).as(JSON)
      }
  }

}