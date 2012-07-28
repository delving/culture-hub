package core

import models.DomainConfiguration
import play.api.mvc._
import util.DomainConfigurationHandler
import eu.delving.templates.scala.GroovyTemplates
import play.api.Logger
import java.util.concurrent.ConcurrentHashMap

trait DomainConfigurationAware {
  self: Controller =>

  private val domainConfigurations = new ConcurrentHashMap[RequestHeader, DomainConfiguration]()

  implicit def configuration(implicit request: RequestHeader) = domainConfigurations.get(request)

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