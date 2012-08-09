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

  /**
   * Queries resources by type and name
   * @param resourceType the type of the resource
   * @param query the query on the resource name
   * @return a sequence of resources matching the query
   */
  def findResources(resourceType: ResourceType, query: String): Seq[Resource]

}



case class ResourceType(resourceType: String)