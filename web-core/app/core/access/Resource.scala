package core.access

import models.OrganizationConfiguration

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
   * The total number of resources of this type
   * @return the number of resources for this ResourceType
   */
  def totalResourceCount(implicit configuration: OrganizationConfiguration): Int

  /**
   * Queries resources name
   * @param orgId the orgId
   * @param query the query on the resource name
   * @return a sequence of resources matching the query
   */
  def findResources(orgId: String, query: String): Seq[Resource]

  /**
   * Queries resources by key
   * @param orgId the orgId
   * @param resourceKey the resourceKey
   * @return the resource of the given key, if found
   */
  def findResourceByKey(orgId: String, resourceKey: String): Option[Resource]

}

case class ResourceType(resourceType: String)