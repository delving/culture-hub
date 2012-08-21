package core.collection

/**
 * TODO extend this again when we'll want per-collection facet management
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Indexable extends Collection {

  def getIndexingMappingPrefix: Option[String]

//  def getIndexingFacetFields: List[String]

//  def getIndexingSortFields: List[String]
}
