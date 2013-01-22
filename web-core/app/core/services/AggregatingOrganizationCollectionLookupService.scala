package core.services

import core.collection.OrganizationCollection
import core.{OrganizationCollectionLookupService, CultureHubPlugin}
import models.OrganizationConfiguration

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class AggregatingOrganizationCollectionLookupService extends OrganizationCollectionLookupService {

  def organizationCollectionLookups(implicit configuration: OrganizationConfiguration): Seq[OrganizationCollectionLookupService] = CultureHubPlugin.getServices(classOf[OrganizationCollectionLookupService])

  def findAll(implicit configuration: OrganizationConfiguration): Seq[OrganizationCollection] = organizationCollectionLookups.flatMap(lookup => lookup.findAll)

  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: OrganizationConfiguration): Option[OrganizationCollection] = organizationCollectionLookups.flatMap(lookup => lookup.findBySpecAndOrgId(spec, orgId)).headOption

}
