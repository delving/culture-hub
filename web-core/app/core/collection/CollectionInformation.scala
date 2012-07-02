package core.collection

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait CollectionInformation extends Collection {

  def getName: String

  def getTotalRecords: Long

}
