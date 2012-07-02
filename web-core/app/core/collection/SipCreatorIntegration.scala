package core.collection

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait SipCreatorIntegration extends Collection {

  def getLockedBy: Option[String]
  def getHashes: Map[String, String]
  def getHints: Array[Byte]


}
