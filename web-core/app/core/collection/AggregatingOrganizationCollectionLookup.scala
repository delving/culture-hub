package core.collection

import core.CultureHubPlugin
import models.DomainConfiguration

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object AggregatingOrganizationCollectionLookup {

  def organizationCollectionLookups(implicit configuration: DomainConfiguration) = CultureHubPlugin.getEnabledPlugins.flatMap(_.organizationCollectionLookups)

  def findAll(implicit configuration: DomainConfiguration): Seq[OrganizationCollection] = organizationCollectionLookups.flatMap(lookup => lookup.findAll(configuration.orgId))

  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: DomainConfiguration): Option[OrganizationCollection] = organizationCollectionLookups.flatMap(lookup => lookup.findBySpecAndOrgId(spec, orgId)).headOption

}
