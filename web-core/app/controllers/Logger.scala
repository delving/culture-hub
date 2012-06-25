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

import java.io.{PrintWriter, StringWriter}
import play.api.Logger
import play.api.Play.current
import play.api.mvc.{RequestHeader, Results, Result}
import models.PortalTheme
import extensions.Email
import core.ThemeInfo
import util.Quotes

/**
 * Unified logging for controllers
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Logging extends Secured { self: ApplicationController =>

  import ErrorReporter._

  def Forbidden(implicit request: RequestHeader): Result                                                 = {
    warning("Forbidden")
    Results.Forbidden
  }
  def Forbidden(why: String)(implicit request: RequestHeader)                                            = {
    warning(why)
    Results.Forbidden(why)
  }
  def NotFound(why: String)(implicit request: RequestHeader)                                             = {
    info(why)
    Results.NotFound(views.html.errors.notFound(request, why, None))
  }
  def Error(implicit request: RequestHeader)        = {
    Logger.error(withContext("Internal server error"))
    reportError(request, "Internal server error", theme)
    Results.InternalServerError(views.html.errors.error(None))
  }
  def Error(why: String)(implicit request: RequestHeader)                              = {
    Logger.error(withContext(why))
    reportError(request, why, theme)
    Results.InternalServerError(why)
  }
  def BadRequest(implicit request: RequestHeader)              = {
    warning("Bad request")
    Results.BadRequest
  }
  def BadRequest(why: String)(implicit request: RequestHeader) = {
    warning(why)
    Results.BadRequest
  }
  def TextError(why: String, status: Int = 500)(implicit request: RequestHeader) = {
    Logger.error(why)
    Results.Status(status)(why)
  }
  def TextNotFound(why: String)(implicit request: RequestHeader) = {
    Logger.warn(why)
    Results.NotFound(why)
  }


  // ~~~ Logger wrappers, with more context
  val CH = "CultureHub"

  def info(message: String, args: String*)(implicit request: RequestHeader) {
    Logger(CH).info(withContext(m(message, args)))
  }
  def info(e: Throwable, message: String, args: String*)(implicit request: RequestHeader) {
    Logger(CH).info(withContext(m(message, args)), e)
  }
  def warning(message: String, args: String*)(implicit request: RequestHeader) {
    Logger(CH).warn(withContext(m(message, args)))
  }
  def warning(e: Throwable, message: String, args: String*)(implicit request: RequestHeader) {
    Logger(CH).warn(withContext(m(message, args)), e)
  }
  def logError(message: String, args: String*)(implicit request: RequestHeader, theme: PortalTheme) {
    Logger(CH).error(withContext(m(message, args)))
    reportError(request, if(message != null) message.format(args) else "", theme)
  }

  def logError(e: Throwable, message: String, args: String*)(implicit request: RequestHeader, theme: PortalTheme) {
    Logger(CH).error(withContext(m(message, args)), e)
    reportError(request, if(message != null) message.format(args) else "", theme)
  }

  def reportSecurity(message: String)(implicit request: RequestHeader)  {
    Logger(CH).error("Attempted security breach: " + message)
    ErrorReporter.reportError(securitySubject, toReport(message, request), theme)
  }
  
  private def m(message: String, args: Seq[String]) = {
    if(args.length > 0) {
      message.format(args : _ *)
    } else {
      message
    }
  }

  private def withContext(msg: String)(implicit request: RequestHeader) = "[%s] While accessing %s %s: %s".format(request.session.get("userName").getOrElse("Unknown"), request.method, request.uri, msg)

  private def securitySubject(implicit request: RequestHeader) = "***[CultureHub] Security alert on %s".format(request.domain)


}

object ErrorReporter {

  def reportError(request: RequestHeader, message: String, theme: PortalTheme) {
    reportError(subject(request), toReport(message, request), theme)
  }
  def reportError(request: RequestHeader, e: Throwable, message: String, theme: PortalTheme) {
    reportError(subject(request), toReport(message, e, request), theme)
  }

  def reportError(job: String, t: Throwable, message: String, theme: PortalTheme) {
    reportError("[CultureHub] An error occured on node %s".format(current.configuration.getString("culturehub.nodeName")), toReport(job, message, t), theme)
  }

  def reportError(subject: String, report: String, theme: PortalTheme) {
    Email(theme.emailTarget.systemFrom, subject).to(theme.emailTarget.exceptionTo).withTemplate("Mails/reportError.txt", "en", 'report -> report, 'quote -> Quotes.randomQuote(), 'themeInfo -> new ThemeInfo(theme)).send()
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
    """
       URL: %s
       METHOD: %s
       HTTP PARAMS:
%s
       HTTP HEADERS:
%s""".format(request.uri,
             request.method,
             request.queryString.map(pair =>         "          " + pair._1 + ": " + pair._2.mkString(", ")).mkString("\n"),
             request.headers.toMap.map(pair =>       "          " + pair._1 + ": " + pair._2.mkString(", ")).mkString("\n"))
  }
}