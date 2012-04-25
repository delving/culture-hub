package controllers.api

import controllers._
import play.api.mvc._
import extensions.JJson
import scala.Predef._
import scala._
import collection.immutable.ListMap
import xml.{NodeSeq, Elem}
import javax.xml.transform.TransformerFactory
import java.io.{ByteArrayOutputStream, ByteArrayInputStream}
import javax.xml.transform.stream.{StreamResult, StreamSource}
import org.apache.commons.lang.StringEscapeUtils

/**
 * The API documentation
 *
 * TODO document all APIs
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Api extends DelvingController {

  def explanations(orgId: String, path: String): Action[AnyContent] = {
    val pathList = path.split("/").drop(1).toList
    if(pathList.isEmpty) {
      api(orgId)
    } else {
      val explanation = pathList(0) match {
        case "proxy" => controllers.api.Proxy.explain(pathList.drop(1))
        case "index" => controllers.api.Index.explain(pathList.drop(1))
        case "search" => return controllers.api.Search.searchApi(orgId, None, None, None)
        case "oai-pmh" => controllers.api.OpenData.explain(pathList.drop(1))
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
          ApiItem("collections", "Collections list"),
          ApiItem("index", "Custom item indexing")
        )

        val description = ApiDescription(apiDescription, apis, globalParams, true, false)
        renderExplanation(description)(request)
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
      if(wantsXml) {
        Ok(<explain>
          <error>{sorry}</error>
        </explain>)
      } else {
        Ok(JJson.generate(Map("error" -> sorry))).as(JSON)
      }
  }


  private def renderExplanation(explanation: Description) = Action {
    implicit request =>
      if(wantsHtml) {
        Ok(explanation.toHtml).as(HTML)
      } else if(wantsXml) {
        Ok(explanation.toXml)
      } else {
        Ok(JJson.generate(explanation.toJson)).as(JSON)
      }
  }

}

abstract class Description {

  def toHtml(implicit request: RequestHeader): String = {

    val xmlSource = new StreamSource(new ByteArrayInputStream(toHtmlXml.toString().getBytes("utf-8")))
    val xsltSource = new StreamSource(new ByteArrayInputStream(toHtmlXslt.toString().getBytes("utf-8")))

    val transFact = TransformerFactory.newInstance()
    val trans = transFact.newTransformer(xsltSource)

    val baos = new ByteArrayOutputStream()

    trans.transform(xmlSource, new StreamResult(baos))

    baos.toString("utf-8")
  }

  def toXml(implicit request: RequestHeader): NodeSeq
  def toJson(implicit request: RequestHeader): Map[String, Any]

  protected def toHtmlXml(implicit request: RequestHeader): NodeSeq
  protected def toHtmlXslt: NodeSeq
}

case class ApiDescription(description: String, apiItems: List[ApiItem] = List.empty, globalParameters: List[ExplainItem] = List.empty, linkUrl: Boolean = false, renderExample: Boolean = true) extends Description {
  def toXml(implicit request: RequestHeader) =
    <explain>
      <description>{description}</description>{if(!apiItems.isEmpty){
      <global-params>{globalParameters.map(_.toXml)}</global-params>}}{if(!apiItems.isEmpty){
      <api-list>{apiItems.map(_.toXml)}</api-list>}}
    </explain>

  def toJson(implicit request: RequestHeader) = Map(
    "description" -> description,
    "global-params" -> globalParameters.map(_.toJson),
    "api-list" -> apiItems.map(_.toJson)
  )


  protected def toHtmlXml(implicit request: RequestHeader): NodeSeq =
    <explain>
      <description>{StringEscapeUtils.escapeXml(description).replaceAll("\n", "<br/>").replaceAll(" ", "&nbsp;")}</description>{if(!apiItems.isEmpty){
      <global-params>{globalParameters.map(_.toXml)}</global-params>}}{if(!apiItems.isEmpty){
      <api-list>{apiItems.map(_.toXml)}</api-list>}}
    </explain>


  def toHtmlXslt: NodeSeq =
    <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
      <html>
      <body>
        <h2>Description</h2>
        <p><xsl:value-of select="explain/description" disable-output-escaping="yes"/></p>
        <xsl:if test="explain/global-parameters">
          <h2>Global parameters</h2>
          <table border="1">
            <tr>
              <th>Label</th>
              <th>Description</th>
              <th>Options</th>
            </tr>
            <xsl:for-each select="explain/global-parameters-list/element">
            <tr>
              <td><xsl:value-of select="label"/></td>
              <td><xsl:value-of select="description"/></td>
              <td>
                <ul>
                  <xsl:for-each select="options">
                    <li><xsl:value-of select="option"/></li>
                  </xsl:for-each>
                </ul>
              </td>
            </tr>
            </xsl:for-each>
          </table>
        </xsl:if>
        <xsl:if test="explain/api-list">
          <h2>API Paths</h2>
          <table border="1">
            <tr>
              <th>URL</th>
              <th>Description</th>
              {if(renderExample) {
              <th>Example</th>
              }}
            </tr>
            <xsl:for-each select="explain/api-list/api">
            <tr>
              <td>{if(linkUrl) {
                <a><xsl:attribute name="href"><xsl:value-of select="url"/>?explain=true</xsl:attribute><xsl:value-of select="url"/></a>
              } else {
                  <xsl:value-of select="url"/>
              }}</td>
              <td><xsl:value-of select="description"/></td>
              {if(renderExample) {
              <td><a><xsl:attribute name="href"><xsl:value-of select="example"/></xsl:attribute><xsl:value-of select="example"/></a></td>
              }}
            </tr>
            </xsl:for-each>
          </table>
        </xsl:if>
      </body>
      </html>
    </xsl:template>
    </xsl:stylesheet>


}

case class ApiCallDescription(description: String, explainItems: List[ExplainItem] = List.empty) extends Description {

  def toXml(implicit request: RequestHeader) =
    <explain>
      <description>{description}</description>
      <parameters>{explainItems.map(_.toXml)}</parameters>
    </explain>

  def toJson(implicit request: RequestHeader) = Map(
    "description" -> description,
    "parameters" -> explainItems.map(_.toJson)
  )


  protected def toHtmlXml(implicit request: RequestHeader): NodeSeq =
    <explain>
      <description>{StringEscapeUtils.escapeXml(description).replaceAll("\n", "<br/>").replaceAll(" ", "&nbsp;")}</description>
      <parameters>{explainItems.map(_.toXml)}</parameters>
    </explain>


  def toHtmlXslt: NodeSeq =
    <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
      <html>
      <body>
        <h2>Description</h2>
        <p><xsl:value-of select="explain/description" disable-output-escaping="true"/></p>
        <xsl:if test="explain/parameters">
          <h2>Parameters</h2>
          <table border="1">
            <tr>
              <th>Label</th>
              <th>Description</th>
              <th>Options</th>
            </tr>
            <xsl:for-each select="explain/parameters/element">
            <tr>
              <td><xsl:value-of select="label"/></td>
              <td><xsl:value-of select="description"/></td>
              <td>
                <ul>
                  <xsl:for-each select="options">
                    <li><xsl:value-of select="option"/></li>
                  </xsl:for-each>
                </ul>
              </td>
            </tr>
            </xsl:for-each>
          </table>
        </xsl:if>
      </body>
      </html>
    </xsl:template>
    </xsl:stylesheet>
}

/**
 * Describes an API path
 */
case class ApiItem(path: String, description: String, example: String = "") {

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
