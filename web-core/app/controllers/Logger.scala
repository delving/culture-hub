/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import java.io.{ PrintWriter, StringWriter }
import play.api.Logger
import play.api.Play.current
import play.api.mvc.{ Controller, RequestHeader, Results, Result }
import models.OrganizationConfiguration
import extensions.Email
import core.ThemeInfo
import util.Quotes

/**
 * Unified logging for controllers
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Logging extends Secured { self: Controller with OrganizationConfigurationAware =>

  protected val log = Logger("CultureHub")

  import ErrorReporter._

  def Forbidden(implicit request: RequestHeader): Result = {
    warning("Forbidden")
    Results.Forbidden
  }
  def Forbidden(why: String)(implicit request: RequestHeader) = {
    warning(why)
    Results.Forbidden(why)
  }
  def NotFound(why: String)(implicit request: RequestHeader) = {
    info(why)
    Results.NotFound(views.html.errors.notFound(request, why, None))
  }
  def Error(implicit request: RequestHeader) = {
    log.error(withContext("Internal server error"))
    reportError(request, "Internal server error")
    Results.InternalServerError(views.html.errors.error(None, None))
  }
  def Error(why: String)(implicit request: RequestHeader) = {
    log.error(withContext(why))
    reportError(request, why)
    Results.InternalServerError(views.html.errors.error(None, Some(why)))
  }
  def Error(why: String, t: Throwable)(implicit request: RequestHeader) = {
    log.error(withContext(why), t)
    reportError(request, t, why)
    Results.InternalServerError(views.html.errors.error(None, Some(why)))
  }

  // ~~~ Logger wrappers, with more context

  def info(message: String, args: String*)(implicit request: RequestHeader) {
    log.info(withContext(m(message, args)))
  }
  def warning(message: String, args: String*)(implicit request: RequestHeader) {
    log.warn(withContext(m(message, args)))
  }
  def logError(message: String, args: String*)(implicit request: RequestHeader, configuration: OrganizationConfiguration) {
    log.error(withContext(m(message, args)))
    reportError(request, if (message != null) message.format(args) else "")
  }

  def logError(e: Throwable, message: String, args: String*)(implicit request: RequestHeader, configuration: OrganizationConfiguration) {
    log.error(withContext(m(message, args)), e)
    reportError(request, if (message != null) message.format(args) else "")
  }

  def reportSecurity(message: String)(implicit request: RequestHeader) {
    log.error("Attempted security breach: " + message)
    ErrorReporter.reportError(securitySubject, toReport(message, request))
  }

  private def m(message: String, args: Seq[String]) = {
    if (args.length > 0) {
      message.format(args: _*)
    } else {
      message
    }
  }

  private def withContext(msg: String)(implicit request: RequestHeader) = {
    "[%s] While accessing %s %s: %s".format(request.session.get("userName").getOrElse("Unknown") + "@" + configuration.orgId, request.method, request.uri, msg)
  }

  private def securitySubject(implicit request: RequestHeader) = "***[CultureHub] Security alert on %s".format(request.domain)

}

object ErrorReporter {

  def reportError(request: RequestHeader, message: String)(implicit configuration: OrganizationConfiguration) {
    reportError(subject(request), toReport(message, request))
  }
  def reportError(request: RequestHeader, e: Throwable, message: String)(implicit configuration: OrganizationConfiguration) {
    reportError(subject(request), toReport(message, e, request))
  }

  def reportError(job: String, t: Throwable, message: String)(implicit configuration: OrganizationConfiguration) {
    reportError("[CultureHub] An error occured on node %s".format(configuration.commonsService.nodeName), toReport(job, message, t))
  }

  def reportError(subject: String, report: String)(implicit configuration: OrganizationConfiguration) {
    val themeInfo = new ThemeInfo(configuration)
    Email(configuration.emailTarget.systemFrom, subject)
      .to(configuration.emailTarget.exceptionTo)
      .withContent(
        """
        |Master,
        |
        |an error has happened:
        |
        |%s
        |
        |
        |----
        |%s
      """.stripMargin.format(report, Quotes.randomQuote()))
      .send()
  }

  private def getUser(request: RequestHeader) = request.session.get("userName").getOrElse("Unknown")

  private def subject(request: RequestHeader) = "[CultureHub] An error occured on %s".format(request.domain) // port?

  def toReport(job: String, m: String, t: Throwable): String = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    t.printStackTrace(pw)
    """    ~~~ Job  ~~~
    %s

    ~~~ Message ~~~
    %s

    ~~~ Throwable message ~~~
    %s

    ~~~ Stacktrace~~~
    %s

    """.format(job, m, t.getMessage, sw.toString)
  }

  def toReport(m: String, request: RequestHeader) = {
    """
    ~~~~ User ~~~
    %s

    ~~~ Message ~~~
    %s

    ~~~ Request context ~~~
    %s""".format(getUser(request), m, fullContext(request))
  }

  def toReport(m: String, t: Throwable, request: RequestHeader): String = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    t.printStackTrace(pw)
    """    ~~~ User ~~~
    %s

    ~~~ Message ~~~
    %s

    ~~~ Throwable message ~~~
    %s

    ~~~ Stacktrace~~~
    %s

    ~~~ Request context ~~~
    %s""".format(getUser(request), m, t.getMessage, sw.toString, fullContext(request))
  }

  private def fullContext(request: RequestHeader) = {
    """|
       |URL: %s
       |METHOD: %s
       |HTTP PARAMS:
       |%s
       |HTTP HEADERS:
       |%s""".stripMargin.format(
      request.uri,
      request.method,
      request.queryString.map(pair => "          " + pair._1 + ": " + pair._2.mkString(", ")).mkString("\n"),
      request.headers.toMap.map(pair => "          " + pair._1 + ": " + pair._2.mkString(", ")).mkString("\n"))
  }
}