package controllers

import models.DomainConfiguration
import play.api.mvc._
import util.DomainConfigurationHandler
import play.api.Logger
import java.util.concurrent.ConcurrentHashMap

trait DomainConfigurationAware {
  self: Controller =>

  private val domainConfigurations = new ConcurrentHashMap[RequestHeader, DomainConfiguration]()

  implicit def configuration(implicit request: RequestHeader): DomainConfiguration = {
    val c = domainConfigurations.get(request)
    if (c == null) {
      Logger("CultureHub").error("Trying to get non-existing configuration for domain " + request.domain)
      null
    } else {
      c
    }
  }

  def DomainConfigured[A](action: Action[A]): Action[A] = {
    Action(action.parser) {
      implicit request =>
        try {
          val domain: String = request.queryString.get("domain").map(v => v.head).getOrElse(request.domain)
          val configuration = DomainConfigurationHandler.getByDomain(domain)
          domainConfigurations.put(request, configuration)
          action(request)
        } catch {
          case t: Throwable =>
            Logger("CultureHub").error(t.getMessage, t)
            throw t
        } finally {
          domainConfigurations.remove(request)
        }
    }
  }

}