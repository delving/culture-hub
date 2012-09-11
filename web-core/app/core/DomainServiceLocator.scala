package core

import models.DomainConfiguration

/**
 * Service Locator using an implicit DomainConfiguration to lookup a service
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait DomainServiceLocator[T <: Any] {

  def byDomain(implicit configuration: DomainConfiguration): T

}
