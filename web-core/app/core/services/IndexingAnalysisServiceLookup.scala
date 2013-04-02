package core.services

import core.{ IndexingAnalysisService, CultureHubPlugin }
import models.OrganizationConfiguration

/**
 * Lookup for [[ core.IndexingAnalysisService ]] instances
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class IndexingAnalysisServiceLookup {

  def findOneBySchemaPrefix(prefix: String)(implicit configuration: OrganizationConfiguration): Option[IndexingAnalysisService] =
    CultureHubPlugin.getServices(classOf[IndexingAnalysisService]).find(_.prefix == prefix)

}
