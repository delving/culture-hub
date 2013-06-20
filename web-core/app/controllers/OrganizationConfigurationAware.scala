package controllers

import models.OrganizationConfiguration
import play.api.mvc._
import util.OrganizationConfigurationHandler
import play.api.Logger
import java.util.concurrent.ConcurrentHashMap

trait OrganizationConfigurationAware {
  self: Controller =>

  private val organizationConfigurations = new ConcurrentHashMap[RequestHeader, OrganizationConfiguration]()

  implicit def configuration(implicit request: RequestHeader): OrganizationConfiguration = {
    val c = organizationConfigurations.get(request)
    if (c == null) {
      Logger("CultureHub").error("Trying to get non-existing configuration for domain " + request.domain)
      null
    } else {
      c
    }
  }

  def OrganizationConfigured[A](action: Action[A]): Action[A] = {
    Action(action.parser) {
      implicit request =>
        val domain: String = request.queryString.get("domain").map(v => v.head).getOrElse(request.domain)
        OrganizationConfigurationHandler.getByDomain(domain).map { implicit configuration =>
          try {
            organizationConfigurations.put(request, configuration)
            action(request)
          } catch {
            case t: Throwable =>
              Logger("CultureHub").error(t.getMessage, t)
              throw t
          } finally {
            organizationConfigurations.remove(request)
          }
        } getOrElse {
          ServiceUnavailable(views.html.errors.serviceUnavailable())
        }
    }
  }

}