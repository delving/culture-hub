package controllers

import play.mvc.Controller
import play.Logger
import scala.collection.JavaConversions.asScalaMap
import scala.collection.JavaConversions.asScalaIterable
import play.mvc.results.{BadRequest, NotFound, Forbidden, Error}
import notifiers.Mails
import java.io.{PrintWriter, StringWriter}
import play.mvc.Http.Request
import play.mvc.Scope.Params
/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Logging extends UserAuthentication { self: Controller =>

  import ErrorReporter._

  override def Forbidden                                       = {
    logWarning("Forbidden")
    new Forbidden("Forbidden")
  }
  override def Forbidden(why: String)                          = {
    logWarning(why)
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
    reportError("Internal server error")
    new Error("Internal server error")
  }
  override def Error(why: String)                              = {
    reportError(why)
    new Error(why)
  }
  override def Error(status: Int, why: String)                 = {
    reportError(status + " " + why)
    new Error(status, why)
  }
  override def BadRequest                                      = {
    logWarning("Bad request")
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

  def info(message: String, args: String*) {
    Logger.warn(withContext(message), args)
  }
  def logInfo(e: Throwable, message: String, args: String*) {
    Logger.warn(e, withContext(message), args)
  }
  def logWarning(message: String, args: String*) {
    Logger.warn(withContext(message), args)
  }
  def logWarning(e: Throwable, message: String, args: String*) {
    Logger.warn(e, withContext(message), args)
  }

  def reportError(message: String, args: String*) {
    request.params.put(CH_ERROR_HANDLED, "YES YES YES")
    Logger.error(withContext(message), args)
    Mails.reportError(subject, toReport(user, message.format(args), request, params))
  }
  def reportError(e: Throwable, message: String, args: String*) {
    Logger.error(e, withContext(message), args)
    Mails.reportError(subject, toReport(user, message.format(args), e, request, params))
  }

  def reportSecurity(message: String) {
    Logger.fatal("Attempted security breach: " + message)
    Mails.reportError(securitySubject, toReport(user, message, request, params))
  }

  def user = (if(connectedUser == null) "Unknown" else connectedUser)

  def withContext(msg: String) = "[%s] While accessing %s: %s".format(user, request.url, msg)

  def subject = "[CultureHub] An error occured on %s".format(request.host + ":" + request.port)

  def securitySubject = "***[CultureHub] Security alert on %s".format(request.host + ":" + request.port)

}

object ErrorReporter {

  val CH_ERROR_HANDLED = "yesVirginia"

  def toReport(user: String, m: String, request: Request, params: Params) =
    """
    ~~~~ User ~~~
    %s

    ~~~ Message ~~~
    %s

    ~~~ Request context ~~~
    %s""".format(user, m, fullContext(request, params))

  def toReport(user: String, m: String, t: Throwable, request: Request, params: Params): String = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    t.printStackTrace(pw)
    """~~~ User ~~~
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

  def fullContext(request: Request, params: Params) =
    """URL: %s
       METHOD: %s
       HTTP PARAMS:
       %s
       HTTP HEADERS:
       %s""".format(request.url,
                    request.method,
                    params.all().map(pair => "   " + pair._1 + ": " + pair._2.mkString(", ").mkString("\n")),
                    request.headers.map(pair => "   " + pair._1 + ": " + pair._2.values.mkString(", ")).mkString("\n"))


}