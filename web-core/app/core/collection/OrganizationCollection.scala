package core.collection

/**
 * Collection that belongs to an organization
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

abstract class OrganizationCollection extends Collection {
  val ownerType: OwnerType.OwnerType = OwnerType.ORGANIZATION
}