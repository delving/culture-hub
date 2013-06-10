package core.collection

/**
 * Basic collection meta-data
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait CollectionMetadata {

  /**
   * Name of the collection
   */
  def getName: String

  /**
   * The total records in a collection
   */
  def getTotalRecords: Long

  /**
   * Optional textual description for a collection
   */
  def getDescription: Option[String]

}