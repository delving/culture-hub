package controllers

import play.api.mvc.{ Result, RequestHeader, Controller }
import xml.NodeSeq

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait RenderingExtensions { self: Controller =>

  // ~~~ API rendering helpers

  def wantsJson(implicit request: RequestHeader) = request.queryString.get("format").isDefined && request.queryString("format").contains("json") ||
    request.queryString.get("format").isEmpty && request.headers.get(ACCEPT).isDefined && request.headers(ACCEPT).contains("application/json")

  def wantsHtml(implicit request: RequestHeader) = request.headers.get(ACCEPT).isDefined && request.headers(ACCEPT).contains("html")

  def wantsXml(implicit request: RequestHeader) = request.queryString.get("format").isDefined && request.queryString("format").contains("xml") ||
    request.queryString.get("format").isEmpty && request.headers.get(ACCEPT).isDefined && request.headers(ACCEPT).contains("application/xml")

  def DOk(xml: NodeSeq, sequences: List[String]*)(implicit request: RequestHeader): Result = {
    if (wantsJson) {
      Ok(util.Json.renderToJson(xml, false, sequences)).as(JSON)
    } else {
      Ok(xml)
    }
  }

}
