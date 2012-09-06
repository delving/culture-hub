package core.collection

import core.{OrganizationCollectionLookupService, CultureHubPlugin}
import models.DomainConfiguration

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object AggregatingOrganizationCollectionLookup {

  def organizationCollectionLookups(implicit configuration: DomainConfiguration): Seq[OrganizationCollectionLookupService] = CultureHubPlugin.getServices(classOf[OrganizationCollectionLookupService])

  def findAll(implicit configuration: DomainConfiguration): Seq[OrganizationCollection] = organizationCollectionLookups.flatMap(lookup => lookup.findAll(configuration.orgId))

  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: DomainConfiguration): Option[OrganizationCollection] = organizationCollectionLookups.flatMap(lookup => lookup.findBySpecAndOrgId(spec, orgId)).headOption

}
