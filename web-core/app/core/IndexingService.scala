package core

import org.apache.solr.common.SolrInputDocument
import models.OrganizationConfiguration

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait IndexingService {

  /**
   * Stages a SOLR InputDocument for indexing, and applies all generic delving mechanisms on top
   */
  def stageForIndexing(doc: SolrInputDocument)(implicit configuration: OrganizationConfiguration)

}
