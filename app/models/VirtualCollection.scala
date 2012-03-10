package models

import controllers.ModelImplicits
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO
import mongoContext._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class VirtualCollection(_id: ObjectId = new ObjectId,
                             spec: String,
                             name: String,
                             orgId: String,
                             dataSetReferences: List[DataSetReference] // kept here for redundancy
                             ) {

  def dataSets: Seq[DataSet] = dataSetReferences.flatMap(r => DataSet.findBySpecAndOrgId(r.spec, r.orgId))

  def namespaces = dataSets.map(_.namespaces).flatten.toMap[String, String]

  def getMetadataFormats(publicCollectionsOnly: Boolean = true): List[RecordDefinition] = {
    // all commonly available formats to all dataSets
    // can probably be done in a more functional way, but how?
    var intersect: List[RecordDefinition] = null
    for(dataSet: DataSet <- dataSets) yield {
      if(intersect == null) {
        intersect = dataSet.getMetadataFormats(publicCollectionsOnly)
      } else {
        intersect = dataSet.getMetadataFormats(publicCollectionsOnly).intersect(intersect)
      }
    }
    intersect
  }

}

case class DataSetReference(spec: String, orgId: String)

// reference to an MDR with a minimal cache to speed up lookups
case class MDRReference(_id: ObjectId = new ObjectId,
                        parentId: ObjectId = new ObjectId,
                        hubCollection: String, // mongo collection in which this one is kept
                        hubId: String, // hubId of the MDR
                        idx: Int, // index, generated at collection creation time, to use as count
                        validOutputFormats: List[String]) // cache of valid output formats


object VirtualCollection extends SalatDAO[VirtualCollection, ObjectId](collection = virtualCollectionsCollection) with ModelImplicits {

  val children = new ChildCollection[MDRReference, ObjectId](collection = virtualCollectionsRecordsCollection, parentIdField = "parentId") {}

  def findAll(orgId: String, accessKey: Option[String] = None): List[VirtualCollection] = {
    // TODO accessKey
    VirtualCollection.find(MongoDBObject("orgId" -> orgId)).toList
  }

  def findBySpecAndOrgId(spec: String, orgId: String) = findOne(MongoDBObject("spec" -> spec, "orgId" -> orgId))

}
