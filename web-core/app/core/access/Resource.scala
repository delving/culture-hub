package core.access

/**
 * A Resource made accessible by a [[models.Role]]
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Resource {

  /** Kind of resource **/
  def getResourceType: ResourceType

  /** unique identifier of the resource **/
  def getResourceKey: String

}

/**
 * The ResourceLookup makes it possible to find resources of a given kind
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait ResourceLookup {

  def resourceType: ResourceType

  /**
   * Queries resources by type and name
   * @param query the query on the resource name
   * @return a sequence of resources matching the query
   */
  def findResources(orgId: String, query: String): Seq[Resource]

}



case class ResourceType(resourceType: String)