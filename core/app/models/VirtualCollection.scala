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

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class VirtualCollection(_id: ObjectId = new ObjectId,
                             spec: String,
                             name: String,
                             orgId: String,
                             query: VirtualCollectionQuery,
                             currentQueryCount: Long = 0,
                             autoUpdate: Boolean = false,
                             dataSetReferences: List[DataSetReference] // kept here for redundancy
                             ) {

  def dataSets: Seq[DataSet] = dataSetReferences.flatMap(r => DataSet.findBySpecAndOrgId(r.spec, r.orgId))

  def namespaces = dataSets.map(_.namespaces).flatten.toMap[String, String]

  def getVisibleMetadataFormats(accessKey: Option[String]): List[RecordDefinition] = {
    // all available formats to all dataSets in common
    // can probably be done in a more functional way, but how?
    var intersect: List[RecordDefinition] = List.empty
    for(dataSet: DataSet <- dataSets) yield {
      if(intersect.isEmpty) {
        intersect = dataSet.getVisibleMetadataFormats(accessKey)
      } else {
        intersect = dataSet.getVisibleMetadataFormats(accessKey).intersect(intersect)
      }
    }
    intersect
  }

  def getPublicMetadataPrefixes = getVisibleMetadataFormats(None).map(_.prefix).asJava

  def recordCount = VirtualCollection.children.countByParentId(_id)

}

case class DataSetReference(spec: String, orgId: String)

case class VirtualCollectionQuery(dataSets: List[String], freeFormQuery: String, excludeHubIds: List[String] = List.empty, theme: String) {

  def toSolrQuery = {
    val specCondition = dataSets.map(s => SPEC + ":" + s + " ").mkString(" ")
    val excludedIdentifiersCondition = "NOT (" + excludeHubIds.map(s => "delving_hubId:\"" + s + "\"").mkString(" OR ") + ")"
    "delving_recordType:mdr " + specCondition + " " + freeFormQuery + (if(!excludeHubIds.isEmpty) " " + excludedIdentifiersCondition else "")
  }
}

// reference to an MDR with a minimal cache to speed up lookups
case class MDRReference(_id: ObjectId = new ObjectId,
                        parentId: ObjectId = new ObjectId,
                        collection: String, // collection in which this one is kept
                        itemId: String, // id of the MDR
                        index: Int, // index, generated at collection creation time, to use as count
                        invalidTargetSchemas: Seq[String]) // cache of invalid output formats


object VirtualCollection extends SalatDAO[VirtualCollection, ObjectId](collection = virtualCollectionsCollection) {

  val children = new ChildCollection[MDRReference, ObjectId](collection = virtualCollectionsRecordsCollection, parentIdField = "parentId") {}

  def findAll(orgId: String): List[VirtualCollection] = VirtualCollection.find(MongoDBObject("orgId" -> orgId)).toList

  def findAllNonEmpty(orgId: String): List[VirtualCollection] = findAll(orgId).filterNot(vc => children.countByParentId(vc._id, MongoDBObject()) == 0)

  def findBySpecAndOrgId(spec: String, orgId: String) = findOne(MongoDBObject("spec" -> spec, "orgId" -> orgId))

  def createVirtualCollectionFromQuery(id: ObjectId, query: String, theme: PortalTheme, connectedUser: String): Either[Throwable, VirtualCollection] = {
    val vc = VirtualCollection.findOneByID(id).getOrElse(return Left(new RuntimeException("Could not find collection with ID " + id)))

    try {
      VirtualCollection.children.removeByParentId(vc._id)

      val hubIds = getIdsFromQuery(query = query, theme = vc.query.theme, connectedUser = connectedUser)
      val groupedHubIds = hubIds.groupBy(id => (id.split("_")(0), id.split("_")(1)))

      val dataSetReferences: List[DataSetReference] = groupedHubIds.flatMap {
        specIds =>
          val orgId = specIds._1._1
          val spec = specIds._1._2
          val ids = specIds._2

          DataSet.findBySpecAndOrgId(spec, orgId) match {

            case Some(ds) =>
              val cache = MetadataCache.get(orgId, spec, ITEM_TYPE_MDR)
              cache.iterate().filter(i => ids.contains(i.itemId)).foreach {
                item =>
                  val ref = MDRReference(parentId = id, collection = spec, itemId = item.itemId, invalidTargetSchemas = item.invalidTargetSchemas, index = item.index)
                  VirtualCollection.children.insert(ref)
              }
              Some(DataSetReference(spec, orgId))

            case None =>
              Logger("CultureHub").warn("Attempting to add entries to Virtual Collection from non-existing DataSet " + spec)
              None
          }
      }.toList

      val count = VirtualCollection.children.countByParentId(id)

      val updatedVc = vc.copy(dataSetReferences = dataSetReferences, currentQueryCount = count)
      VirtualCollection.save(updatedVc)
      Right(updatedVc)

    } catch {
      case mqe: MalformedQueryException => return Left(mqe)
      case t => return Left(t)
    }
  }

  private def getIdsFromQuery(query: String, start: Int = 0, ids: ListBuffer[String] = ListBuffer.empty, theme: String, connectedUser: String): List[String] = {

    // for the start, only pass a dead-simple query
    val portalTheme = PortalTheme.getAll.find(_.name == theme).get
    val params = Params(Map("query" -> Seq(query), "start" -> Seq(start.toString)))
    val chQuery: CHQuery = SolrQueryService.createCHQuery(params, portalTheme, true, Option(connectedUser), List.empty[String])
    val response = CHResponse(params, portalTheme, SolrQueryService.getSolrResponseFromServer(chQuery.solrQuery, true), chQuery)
    val briefItemView = BriefItemView(response)
    val hubIds = briefItemView.getBriefDocs.map(_.getHubId).filterNot(_.isEmpty)
    Logger("CultureHub").debug("Found ids " + hubIds)
    ids ++= hubIds

    if(briefItemView.getPagination.isNext) {
      getIdsFromQuery(query, briefItemView.getPagination.getNextPage, ids, theme, connectedUser)
    }
    ids.toList
  }

}
