package core.collection

/**
 * The mother of all Collections
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

abstract class Collection {

  // the identifier of this collection. may only contain alphanumericals and dashes
  val spec: String

  // the type of owner
  val ownerType: OwnerType.OwnerType

  // the userName of the creator of this collection
  def getCreator: String

  // the owner of this collection. This may be an orgId or userName
  def getOwner: String

}

object OwnerType extends Enumeration("USER", "ORGANIZTAION") {
  type OwnerType = Value
  val USER, ORGANIZATION = Value
}