package controllers

import models.OrganizationConfiguration
import play.api.mvc._
import util.OrganizationConfigurationHandler

/**
 * Multitenancy support is provided through the MultitenantAction and MultitenantRequest. The MultitenantRequest wraps
 * a typical Play Request and adds the configuration of the currently accessed domain to it.
 */
trait MultitenancySupport { self: Controller =>

  case class MultitenantAction[A](bp: BodyParser[A])(f: MultitenantRequest[A] => Result) extends Action[A] with Rendering {

    def apply(request: Request[A]): Result = request match {
      case r: MultitenantRequest[A] => f(r)
      case _ => {
        OrganizationConfigurationHandler.byDomain(request.domain).right.toOption.flatMap { maybeConfiguration =>
          {
            maybeConfiguration.map { configuration =>
              f(MultitenantRequest(configuration, request))
            }
          }
        } getOrElse {
          render {
            case Accepts.Html() => ServiceUnavailable(views.html.errors.serviceUnavailable())
            case _ => ServiceUnavailable
          }(request)
        }
      }
    }

    lazy val parser = bp
  }

  object MultitenantAction {
    def apply(block: MultitenantRequest[AnyContent] => Result): MultitenantAction[AnyContent] = apply(BodyParsers.parse.anyContent)(block)
    def apply(block: => Result): MultitenantAction[AnyContent] = apply(BodyParsers.parse.anyContent)(_ => block)
  }

  case class MultitenantRequest[A](configuration: OrganizationConfiguration, private val request: Request[A]) extends WrappedRequest(request)

  implicit def configuration[A](implicit request: MultitenantRequest[A]): OrganizationConfiguration = request.configuration

}