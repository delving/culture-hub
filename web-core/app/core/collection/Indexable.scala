package core.collection

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Indexable extends Collection {

  def getIndexingMappingPrefix: Option[String]

  def getIndexingFacetFields: List[String]

  def getIndexingSortFields: List[String]
}
