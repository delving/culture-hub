package core.services

import core.collection.OrganizationCollection
import core.{OrganizationCollectionLookupService, CultureHubPlugin}
import models.DomainConfiguration

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class AggregatingOrganizationCollectionLookupService extends OrganizationCollectionLookupService {

  def organizationCollectionLookups(implicit configuration: DomainConfiguration): Seq[OrganizationCollectionLookupService] = CultureHubPlugin.getServices(classOf[OrganizationCollectionLookupService])

  def findAll(implicit configuration: DomainConfiguration): Seq[OrganizationCollection] = organizationCollectionLookups.flatMap(lookup => lookup.findAll)

  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: DomainConfiguration): Option[OrganizationCollection] = organizationCollectionLookups.flatMap(lookup => lookup.findBySpecAndOrgId(spec, orgId)).headOption

}
