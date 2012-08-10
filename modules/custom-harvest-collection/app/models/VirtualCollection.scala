package models

import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO
import mongoContext._
import core.Constants._
import scala.collection.JavaConverters._
import play.api.Logger
import collection.mutable.ListBuffer
import core.search._
import core.collection.Harvestable
import util.DomainConfigurationHandler
import core.harvesting.AggregatingHarvestCollectionLookup

/**
 * A VirtualCollection is a collection resulting from a search, with references to the search results being cached.
 * The aim is to be able to harvest a fine-tuned set of search results.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class VirtualCollection(_id: ObjectId = new ObjectId,
                             spec: String,
                             name: String,
                             orgId: String,
                             creator: String, // userName of the creator
                             query: VirtualCollectionQuery,
                             currentQueryCount: Long = 0,
                             autoUpdate: Boolean = false,
                             dataSetReferences: List[DataSetReference] // kept here for redundancy
                              ) extends Harvestable {

  implicit val configuration = DomainConfigurationHandler.getByOrgId(orgId)

  // ~~~ basics
  def getName: String = name

  def getCreator: String = creator

  def getOwner: String = orgId

  def getTotalRecords = VirtualCollection.dao(orgId).children.countByParentId(_id)


  // ~~~ VC specific

  def collections: Seq[Harvestable] = dataSetReferences.flatMap { reference =>
    AggregatingHarvestCollectionLookup.findBySpecAndOrgId(reference.spec, reference.orgId)
  }.filterNot { instance =>
    instance.isInstanceOf[VirtualCollection] }

  def getPublicMetadataPrefixes = getVisibleMetadataSchemas(None).map(_.prefix).asJava

  // ~~~ harvesting
  def getRecords(metadataFormat: String, position: Int, limit: Int): (List[MetadataItem], Long) = {
    val children = VirtualCollection.dao(orgId).children
    val references = children.find(MongoDBObject("parentId" -> _id) ++ ("invalidTargetSchemas" $ne metadataFormat) ++ ("index" $gt position)).sort(MongoDBObject("index" -> 1)).limit(limit)
    val totalSize = children.count(MongoDBObject("parentId" -> _id) ++ ("invalidTargetSchemas" $ne metadataFormat) ++ ("index" $gt position))
    val records = references.toList.groupBy(_.collection).map {
      grouped =>
        val cache = MetadataCache.get(orgId, grouped._1, ITEM_TYPE_MDR)
        cache.list()
    }.flatten.toList
    (records, totalSize.toInt)
  }

  def getVisibleMetadataSchemas(accessKey: Option[String]): Seq[RecordDefinition] = {
    // all available formats to all dataSets in common
    // can probably be done in a more functional way, but how?
    var intersect: Seq[RecordDefinition] = List.empty
    for (harvestable: Harvestable <- collections) yield {
      if (intersect.isEmpty) {
        intersect = harvestable.getVisibleMetadataSchemas(accessKey)
      } else {
        intersect = harvestable.getVisibleMetadataSchemas(accessKey).intersect(intersect)
      }
    }
    intersect
  }

  def getNamespaces = collections.map(_.getNamespaces).flatten.toMap[String, String]


}

case class DataSetReference(spec: String, orgId: String)

case class VirtualCollectionQuery(dataSets: List[String], freeFormQuery: String, excludeHubIds: List[String] = List.empty, domainConfiguration: String) {

  def toSolrQuery = {
    val specCondition = dataSets.map(s => SPEC + ":" + s + " ").mkString(" ")
    val excludedIdentifiersCondition = "NOT (" + excludeHubIds.map(s => "delving_hubId:\"" + s + "\"").mkString(" OR ") + ")"
    "delving_recordType:mdr " + specCondition + " " + freeFormQuery + (if (!excludeHubIds.isEmpty) " " + excludedIdentifiersCondition else "")
  }
}

// reference to an MDR with a minimal cache to speed up lookups
case class MDRReference(_id: ObjectId = new ObjectId,
                        parentId: ObjectId = new ObjectId,
                        collection: String, // collection in which this one is kept
                        itemId: String, // id of the MDR
                        index: Int, // index, generated at collection creation time, to use as count
                        invalidTargetSchemas: Seq[String] // cache of invalid output formats
                       )


object VirtualCollection extends MultiModel[VirtualCollection, VirtualCollectionDAO] {

  protected def connectionName: String = "VirtualCollections"

  protected def initIndexes(collection: MongoCollection) {}

  protected def initDAO(collection: MongoCollection, connection: MongoDB): VirtualCollectionDAO = new VirtualCollectionDAO(collection, connection)
}

class VirtualCollectionDAO(collection: MongoCollection, connection: MongoDB) extends SalatDAO[VirtualCollection, ObjectId](collection) {

  val virtualCollectionsRecordsCollection = connection("VirtualCollectionsRecords")

  val children = new ChildCollection[MDRReference, ObjectId](collection = virtualCollectionsRecordsCollection, parentIdField = "parentId") {}

  def findAll(orgId: String): List[VirtualCollection] = find(MongoDBObject("orgId" -> orgId)).toList

  def findAllNonEmpty(orgId: String): List[VirtualCollection] = findAll(orgId).filterNot(vc => children.countByParentId(vc._id, MongoDBObject()) == 0)

  def findBySpecAndOrgId(spec: String, orgId: String) = findOne(MongoDBObject("spec" -> spec, "orgId" -> orgId))

  def createVirtualCollectionFromQuery(id: ObjectId, query: String, connectedUser: Option[String])(implicit configuration: DomainConfiguration): Either[Throwable, VirtualCollection] = {
    val vc = findOneById(id).getOrElse(return Left(new RuntimeException("Could not find collection with ID " + id)))

    try {
      children.removeByParentId(vc._id)

      val hubIds = getIdsFromQuery(query = query, connectedUser = connectedUser)(DomainConfigurationHandler.getByName(vc.query.domainConfiguration))
      val groupedHubIds = hubIds.groupBy(id => (id.split("_")(0), id.split("_")(1)))

      val dataSetReferences: List[DataSetReference] = groupedHubIds.flatMap {
        specIds =>
          val orgId = specIds._1._1
          val spec = specIds._1._2
          val ids = specIds._2

          AggregatingHarvestCollectionLookup.findBySpecAndOrgId(spec, orgId) match {

            case Some(ds) =>
              val cache = MetadataCache.get(orgId, spec, ITEM_TYPE_MDR)
              cache.iterate().filter(i => ids.contains(i.itemId)).foreach {
                item =>
                  val ref = MDRReference(parentId = id, collection = spec, itemId = item.itemId, invalidTargetSchemas = item.invalidTargetSchemas, index = item.index)
                  children.insert(ref)
              }
              Some(DataSetReference(spec, orgId))

            case None =>
              Logger("CultureHub").warn("Attempting to add entries to Virtual Collection from non-existing DataSet " + spec)
              None
          }
      }.toList

      val count = children.countByParentId(id)

      val updatedVc = vc.copy(dataSetReferences = dataSetReferences, currentQueryCount = count)
      save(updatedVc)
      Right(updatedVc)

    } catch {
      case mqe: MalformedQueryException => return Left(mqe)
      case t => return Left(t)
    }
  }

  private def getIdsFromQuery(query: String, start: Int = 0, ids: ListBuffer[String] = ListBuffer.empty, connectedUser: Option[String])(implicit configuration: DomainConfiguration): List[String] = {

    // for the start, only pass a dead-simple query
    val params = Params(Map("query" -> Seq(query), "start" -> Seq(start.toString)))
    val chQuery: CHQuery = SolrQueryService.createCHQuery(params, connectedUser, List.empty[String])
    val response = CHResponse(params, configuration, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery)
    val briefItemView = BriefItemView(response)
    val hubIds = briefItemView.getBriefDocs.map(_.getHubId).filterNot(_.isEmpty)
    Logger("CultureHub").debug("Found ids " + hubIds)
    ids ++= hubIds

    if (briefItemView.getPagination.isNext) {
      getIdsFromQuery(query, briefItemView.getPagination.getNextPage, ids, connectedUser)
    }
    ids.toList
  }

}
