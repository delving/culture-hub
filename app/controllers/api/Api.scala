package controllers.api

import controllers.DelvingController
import play.api.mvc._
import extensions.JJson
import core.search.ExplainItem

/**
 * Delving API
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Api extends DelvingController {

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
            <global-parameters>
              {globalParams.map {
              param => param.toXML
            }}
            </global-parameters>
            <api-list>
            {apis.map {
            api =>
              <api>
                <url>{api.url}</url>
                <description>{api.description}</description>
                {if(!api.example.isEmpty) {
                <example>{api.exampleUrl}</example>
                } else {
                <example />}}
              </api>
          }}
          </api-list>
          </explain>
          Ok(xml)
        } else {
          val json = Map(
            "description" -> apiDescription,
            "global-parameters" -> globalParams.map(_.toJson),
            "api-list" -> apis.map(a => Map(
                "url" -> a.url,
                "description" -> a.description,
                "example" -> (if(!a.example.isEmpty) a.exampleUrl else "")
                ))
          )
          Ok(JJson.generate(json)).as(JSON)
        }
    }
  }

}

case class ApiDescription(path: String, description: String, example: String = "") {

  def url(implicit request: RequestHeader) = "http://" + request.host + request.path + "/" + path
  def exampleUrl(implicit request: RequestHeader) = "http://" + request.host + request.path + "/" + example

}

case class GlobalParam(name: String, description: String)