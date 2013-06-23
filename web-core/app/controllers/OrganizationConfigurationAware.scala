package controllers

import models.OrganizationConfiguration
import play.api.mvc._
import util.OrganizationConfigurationHandler

trait OrganizationConfigurationAware { self: Controller =>

  case class MultitenantAction[A](f: MultitenantRequest[A] => Result)(bp: BodyParser[A]) extends Action[A] with Rendering {

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
    def apply[A](bp: BodyParser[A])(block: MultitenantRequest[A] => Result): MultitenantAction[A] = new MultitenantAction[A](block)(bp)
    def apply(block: MultitenantRequest[AnyContent] => Result): MultitenantAction[AnyContent] = apply(BodyParsers.parse.anyContent)(block)
    def apply(block: => Result): MultitenantAction[AnyContent] = apply(BodyParsers.parse.anyContent)(_ => block)
  }

  case class MultitenantRequest[A](configuration: OrganizationConfiguration, private val request: Request[A]) extends WrappedRequest(request)

  implicit def configuration[A](implicit request: MultitenantRequest[A]): OrganizationConfiguration = request.configuration

  // the following methods are here for backwards-compatibility, their functionality is now contained withing the MultitenantAction
  // TODO track down and remove all instances of this wrapped in favour of MultitenantAction

  def OrganizationConfigured[A](p: BodyParser[A])(f: MultitenantRequest[A] => Result) = {
    MultitenantAction(p) { implicit request => f(request)
    }
  }

  def OrganizationConfigured(f: MultitenantRequest[AnyContent] => Result): Action[AnyContent] = {
    OrganizationConfigured(parse.anyContent)(f)
  }

}