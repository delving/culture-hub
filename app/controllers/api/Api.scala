package controllers.api

import controllers._
import play.api.mvc._
import extensions.JJson
import scala.Predef._
import scala._
import xml.NodeSeq
import org.apache.commons.lang.StringEscapeUtils
import core.ExplainItem

/**
 * The API documentation
 *
 * TODO document all APIs
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Api extends DelvingController with RenderingExtensions {

  def explanations(orgId: String, path: String): Action[AnyContent] = {
    val pathList = path.split("/").drop(1).toList
    if (pathList.isEmpty) {
      api(orgId)
    } else {
      val explanation = pathList(0) match {
        case "proxy" => controllers.api.Proxy.explain(pathList.drop(1))
        case "index" => controllers.api.Index.explain(pathList.drop(1))
        case _ => return noDocumentation(orgId, path)
      }
      explanation match {
        case Some(e) => renderExplanation(e)
        case None => noDocumentation(orgId, path)
      }
    }
  }

  /**
   * Index of APIs. The idea is not to explain each of them in detail, the root path of each should do that instead
   */
  def api(orgId: String) = Root {
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

  def explain(orgId: String) = Action {
    implicit request => explainPath(orgId, request.path)(request)
  }

  /** routes to the appropriate explain response **/
  def explainPath(orgId: String, path: String): Action[AnyContent] = {
    val apiPath = path.substring(("/organizations/" + orgId + "/api").length)
    explanations(orgId, apiPath)
  }

  def noDocumentation(orgId: String, path: String) = Action {
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