package core.storage

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

abstract class Collection {

  val orgId: String

  val name: String

  def databaseName: String

}
