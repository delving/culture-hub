package models

import com.mongodb.casbah.Imports._

/**
 * Hub Collection access. This covers both real collections (DataSets) and virtual ones (VirtualCollections)
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Collection(spec: String,
                      orgId: String,
                      name: String,
                      namespaces: Map[String, String]) {


  def getRecords(metadataFormat: String, position: Int, limit: Int): (List[MetadataRecord], Int) = {
    val dataSet = DataSet.findBySpecAndOrgId(spec, orgId)
    if(dataSet.isDefined) {
      val records = DataSet.getRecords(orgId, spec).find((MongoDBObject("validOutputFormats" -> metadataFormat)  ++ ("transferIdx" $gt position) )).sort(MongoDBObject("transferIdx" -> 1)).limit(limit)
      val totalSize = DataSet.getRecords(orgId, spec).count((MongoDBObject("validOutputFormats" -> metadataFormat)  ++ ("transferIdx" $gt position) ))
      (records.toList, totalSize.toInt)
    } else {
      VirtualCollection.findBySpecAndOrgId(spec, orgId) match {
        case Some(vc) =>
          val references = VirtualCollection.children.find(MongoDBObject("parentId" -> vc._id, "validOutputFormats" -> metadataFormat) ++ ("idx" $gt position)).sort(MongoDBObject("idx" -> 1)).limit(limit)
          val totalSize = VirtualCollection.children.count(MongoDBObject("parentId" -> vc._id, "validOutputFormats" -> metadataFormat) ++ ("idx" $gt position))
          val records = references.toList.groupBy(_.hubCollection).map {
            grouped => MetadataRecord.getMDRs(grouped._1, grouped._2.map(_.hubId))
          }.flatten.toList
          (records, totalSize.toInt)
        case None => (List.empty, 0)
      }
    }
  }
  
}

object Collection {

  def findAll(orgId: String, accessKey: Option[String] = None): List[Collection] = {

    // TODO implement accessKey lookup
    val dataSets: List[Collection] = DataSet.findAll(orgId)
    val virtualCollections: List[Collection] = VirtualCollection.findAll(orgId)

    dataSets ++ virtualCollections
  }

  def findBySpecAndOrgId(spec: String, orgId: String): Option[Collection] = {
    val maybeDataSet = DataSet.findBySpecAndOrgId(spec, orgId)
    if(maybeDataSet.isDefined) {
      val collection: Collection = maybeDataSet.get
      Some(collection)
    } else {
      val maybeVirtualCollection = VirtualCollection.findBySpecAndOrgId(spec, orgId)
      if(maybeVirtualCollection.isDefined) {
        val collection: Collection = maybeVirtualCollection.get
        Some(collection)
      } else {
        None
      }
    }
  }

  def getMetadataFormats(spec: String, orgId: String, accessKey: Option[String]): Seq[RecordDefinition] = {
    val dataSets = DataSet.findAll(orgId)
    val virtualCollections = VirtualCollection.findAll(orgId)

    val formats = if (dataSets.exists(_.spec == spec)) {
      DataSet.getMetadataFormats(spec, orgId, accessKey)
    } else if (virtualCollections.exists(_.spec == spec)) {
      VirtualCollection.findBySpecAndOrgId(spec, orgId).map {
        // TODO check this
        vc => vc.getVisibleMetadataFormats(accessKey)
      }.getOrElse(Seq())
    } else {
      Seq()
    }
    
    formats.distinct
  }

  /**
   * Gets all publicly available formats out there, plus the ones available via the accessKey.
   */
  def getAllMetadataFormats(orgId: String, accessKey: Option[String]): List[RecordDefinition] = {
    DataSet.getAllVisibleMetadataFormats(orgId, accessKey).distinct
  }


  

  private implicit def dataSetToCollection(dataSet: DataSet): Collection = Collection(dataSet.spec, dataSet.orgId, dataSet.details.name, dataSet.namespaces)

  private implicit def dataSetListToCollectoinList(dataSets: List[DataSet]): List[Collection] = dataSets.map(dataSetToCollection(_))

  private implicit def virtualCollectionToCollection(vc: VirtualCollection): Collection = Collection(vc.spec, vc.orgId, vc.name, vc.namespaces)

  private implicit def virtualCollectionListToCollectionList(vcs: List[VirtualCollection]): List[Collection] = vcs.map(virtualCollectionToCollection(_))

  

}
