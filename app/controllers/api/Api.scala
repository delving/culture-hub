package controllers.api

import controllers.DelvingController
import play.api.mvc._
import extensions.JJson
import xml.Elem
import collection.immutable.ListMap
import scala.Predef._

/**
 * The API documentation
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Api extends DelvingController {

  /** routes to the appropriate explain response **/
  def explainPath(orgId: String, path: String): Action[AnyContent] = {
    val apiPath = path.substring(("/organizations/" + orgId + "/api").length)
    apiPath match {

      case "" => Api.api(orgId)
      case "/proxy" => renderExplanation(Proxy.explain(orgId))
      case _ => noDocumentation(orgId, path)
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
          ApiDescription("search", "Search API", "search?explain=true"),
          ApiDescription("search/provider", "Search API by provider", "search/provider/delving?query=test"),
          ApiDescription("search/dataProvider", "Search API by dataProvider", "search/dataProvider/delving?query=test"),
          ApiDescription("search/collection", "Search API by collection", "search/collection/delving?query=test"),
          ApiDescription("oai-pmh", "OAI-PMH access point", "oai-pmh?verb=Identify"),
          ApiDescription("proxy", "Search proxy"),
          ApiDescription("providers", "Providers list"),
          ApiDescription("dataProviders", "Data Providers list"),
          ApiDescription("collections", "Collections list")
        )

        if(wantsXml) {
          val xml = <explain>
            <description>{apiDescription}</description>
            <global-parameters>{globalParams.map(_.toXml)}</global-parameters>
            <api-list>{apis.map(_.toXml)}</api-list>
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

  def noDocumentation(orgId: String, path: String) = Action {
    implicit request =>
      val sorry = "Sorry, no documentation found for path " + path
      if(wantsXml) {
        Ok(<explain>
          <error>{sorry}</error>
        </explain>)
      } else {
        Ok(JJson.generate(Map("error" -> sorry))).as(JSON)
      }
  }


  private def renderExplanation(explanation: List[ApiDescription]) = Action {
    implicit request =>
      if(wantsXml) {
        val xml = <explain>
          <api-list>
            {explanation.map {
            a => a.toXml}}
          </api-list>
        </explain>
        Ok(xml)
      } else {
        val json = Map(
          "api-list" -> explanation.map(_.toJson)
        )
        Ok(JJson.generate(json)).as(JSON)
      }
  }


}

/**
 * Describes an API path
 */
case class ApiDescription(path: String, description: String, example: String = "") {

  def url(implicit request: RequestHeader) = "http://" + request.host + request.path + "/" + path
  def exampleUrl(implicit request: RequestHeader) = "http://" + request.host + request.path + "/" + example

  def toXml(implicit request: RequestHeader) = <api>
                <url>{url}</url>
                <description>{description}</description>
                {if(!example.isEmpty) {
                <example>{exampleUrl}</example>
                } else {
                <example />}}
              </api>

  def toJson(implicit request: RequestHeader) = Map(
                "url" -> url,
                "description" -> description,
                "example" -> (if(!example.isEmpty) exampleUrl else "")
               )

}

/**
 * Describes an API parameter
 */
case class ExplainItem(label: String, options: List[String] = List(), description: String = "") {

  def toXml: Elem = {
    <element>
      <label>
        {label}
      </label>{if (!options.isEmpty)
      <options>
        {options.map(option => <option>
        {option}
      </option>)}
      </options>}{if (!description.isEmpty) <description>
      {description}
    </description>}
    </element>
  }

  def toJson: ListMap[String, Any] = {
    if (!options.isEmpty && !description.isEmpty)
      ListMap("label" -> label, "options" -> options.toSeq, "description" -> description)
    else if (!options.isEmpty)
      ListMap("label" -> label, "options" -> options.toSeq)
    else
      ListMap("label" -> label)
  }
}