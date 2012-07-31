package core

import models.DomainConfiguration
import play.api.mvc._
import util.DomainConfigurationHandler
import play.api.Logger
import java.util.concurrent.ConcurrentHashMap

trait DomainConfigurationAware {
  self: Controller =>

  private val domainConfigurations = new ConcurrentHashMap[RequestHeader, DomainConfiguration]()

  implicit def configuration(implicit request: RequestHeader) = {
    val c = domainConfigurations.get(request)
    if(c == null) {
      Logger("CultureHub").error("Trying to get non-existing configuration for domain " + request.domain)
    } else {
      c
    }
  }

  def DomainConfigured[A](action: Action[A]): Action[A] = {
    Action(action.parser) {
      implicit request =>
        try {
          val configuration = DomainConfigurationHandler.getByDomain(request.domain)
          domainConfigurations.put(request, configuration)
          action(request)
        } catch {
          case t =>
            Logger("CultureHub").error(t.getMessage, t)
            throw t
        } finally {
          domainConfigurations.remove(request)
        }
    }
  }

}