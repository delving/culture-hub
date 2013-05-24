package core

import models.OrganizationConfiguration

/**
 * Service Locator using an implicit OrganizationConfiguration to lookup a service
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait DomainServiceLocator[T <: Any] {

  def byDomain(implicit configuration: OrganizationConfiguration): T

}