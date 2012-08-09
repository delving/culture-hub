package models

import core.access.{ResourceType, Resource, ResourceLookup}
import com.mongodb.casbah.commons.MongoDBObject
import java.util.regex.Pattern

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSetResourceLookup extends ResourceLookup {

  def resourceType: ResourceType = DataSet.RESOURCE_TYPE

  /**
   * Queries resources by type and name
   * @param orgId the orgId
   * @param query the query on the resource name
   * @return a sequence of resources matching the query
   */
  def findResources(orgId: String, query: String): Seq[Resource] = {
    DataSet.dao(orgId).find(
      MongoDBObject(
        "orgId" -> orgId,
        "spec" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE)
      )
    ).toSeq
  }
}
