package controllers

import play.mvc.Controller
import scala.collection.JavaConversions.asScalaMap
import scala.collection.JavaConversions.asScalaIterable
import play.mvc.results.{BadRequest, NotFound, Forbidden, Error}
import notifiers.Mails
import java.io.{PrintWriter, StringWriter}
import play.mvc.Http.Request
import play.mvc.Scope.Params
import play.Logger

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Logging extends UserAuthentication { self: Controller =>

  import ErrorReporter._

  override def Forbidden                                       = {
    warning("Forbidden")
    new Forbidden("Forbidden")
  }
  override def Forbidden(why: String)                          = {
    warning(why)
    new Forbidden(why)
  }
  override def NotFound                                        = {
    info("Not found")
    new NotFound("Not found")
  }
  override def NotFound(why: String)                           = {
    info(why)
    new NotFound(why)
  }
  override def NotFound(method: String, path: String)          = {
    info(method + " " + path)
    new NotFound(method, path)
  }
  override def Error                                           = {
    Logger.error(withContext("Internal server error"))
    reportError(request, params, user, "Internal server error")
    new Error("Internal server error")
  }
  override def Error(why: String)                              = {
    Logger.error(withContext(why))
    reportError(request, params, user, why)
    new Error(why)
  }
  override def Error(status: Int, why: String)                 = {
    Logger.error(withContext(why))
    reportError(request, params, user, status + " " + why)
    new Error(status, why)
  }
  override def BadRequest                                      = {
    warning("Bad request")
    new BadRequest()
  }
  def TextError(why: String, status: Int = 500) = {
    response.status = new java.lang.Integer(status)
    Logger.error(why)
    Text(why)
  }
  def TextNotFound(why: String) = {
    response.status = new java.lang.Integer(404)
    Logger.warn(why)
    Text(why)
  }


  // ~~~ Logger wrappers, with more context

  def info(message: String, args: String*) {
    Logger.info(withContext(message), args : _ *)
  }
  def info(e: Throwable, message: String, args: String*) {
    Logger.info(e, withContext(message), args : _ *)
  }
  def warning(message: String, args: String*) {
    Logger.warn(withContext(message), args : _ *)
  }
  def warning(e: Throwable, message: String, args: String*) {
    Logger.warn(e, withContext(message), args : _ *)
  }
  def logError(message: String, args: String*) {
    Logger.error(withContext(message), args : _ *)
  }
  def logError(e: Throwable, message: String, args: String*) {
    Logger.error(e, withContext(message), args : _ *)
  }

  def reportSecurity(message: String) {
    Logger.fatal("Attempted security breach: " + message)
    Mails.reportError(securitySubject, toReport(user, message, request, params))
  }

  private def withContext(msg: String) = "[%s] While accessing %s: %s".format(user, request.url, msg)

  private def securitySubject = "***[CultureHub] Security alert on %s".format(request.host + ":" + request.port)

  private def user = (if(connectedUser == null) "Unknown" else connectedUser)

}

object ErrorReporter {

  def reportError(request: Request, params: Params, user: String, message: String, args: String*) {
    Mails.reportError(subject(request), toReport(user, message.format(args : _ *), request, params))
  }
  def reportError(request: Request, params: Params, user: String, e: Throwable, message: String, args: String*) {
    Mails.reportError(subject(request), toReport(user, message.format(args : _ *), e, request, params))
  }

  def reportError(job: String, t: Throwable, message: String) {
    Mails.reportError("[CultureHub] An error occured on node %s".format(play.Play.configuration.getProperty("culturehub.nodeName")), toReport(job, message, t))
  }

  private def subject(request: Request) = "[CultureHub] An error occured on %s".format(request.host + ":" + request.port)

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

  def toReport(user: String, m: String, request: Request, params: Params) = {
    """
    ~~~~ User ~~~
    %s

    ~~~ Message ~~~
    %s

    ~~~ Request context ~~~
    %s""".format(user, m, fullContext(request, params))
  }

  def toReport(user: String, m: String, t: Throwable, request: Request, params: Params): String = {
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
    %s""".format(user, m, t.getMessage, sw.toString, fullContext(request, params))
  }

  private def fullContext(request: Request, params: Params) = {
    """
       URL: %s
       METHOD: %s
       HTTP PARAMS:
%s
       HTTP HEADERS:
%s""".format(request.url,
                    request.method,
                    params.all().map(pair => "          " + pair._1 + ": " + pair._2.mkString(", ")).mkString("\n"),
                    request.headers.map(pair => "          " + pair._1 + ": " + pair._2.values.mkString(", ")).mkString("\n"))
  }
}