package core.collection

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait CollectionInformation {

  def getName: String

  def getTotalRecords: Long

  def getDescription: Option[String]

}