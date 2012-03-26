/*
 * Copyright 2012 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

import com.mongodb.casbah.Imports._
import exceptions.{MappingNotFoundException, InvalidIdentifierException, RecordNotFoundException}
import org.bson.types.ObjectId
import java.util.Date
import models.mongoContext._
import com.novus.salat.grater
import util.Constants._
import com.novus.salat.dao.SalatDAO
import com.mongodb.WriteConcern
import core.mapping.MappingService

case class MetadataRecord(_id: ObjectId = new ObjectId,
                          hubId: String,
                          rawMetadata: Map[String, String], // this is the raw xml data string, in various mapped formats and in origin (as "raw")
                          summaryFields: Map[String, String] = Map.empty, // the map containing the summary fields for this record, if available
                          modified: Date = new Date(),
                          validOutputFormats: List[String] = List.empty[String], // valid formats this records can be mapped to
                          deleted: Boolean = false, // if the record has been deleted
                          transferIdx: Option[Int] = None, // 0-based index for the transfer order
                          localRecordKey: String, // the unique element value
                          links: List[EmbeddedLink] = List.empty[EmbeddedLink],
                          globalHash: String, // the hash of the raw content
                          hash: Map[String, String] // the hash for each field, for duplicate detection
                           ) {

  def pmhId = hubId.replaceAll("_", ":")

  def getUri(orgId: String, spec: String) = "http://%s/%s/object/%s/%s".format(getNode, orgId, spec, localRecordKey)

  def getRawXmlString = rawMetadata("raw")

  def getCachedTransformedRecord(metadataPrefix: String): String = {
    if (rawMetadata.contains(metadataPrefix)) {
      rawMetadata.get(metadataPrefix).get
    } else {
      throw new RecordNotFoundException("Unable to find record with source metadata prefix: %s".format(metadataPrefix))
    }
  }

  def getDefaultAccessor = {
    new SummaryFieldsMapMetadataAccessors(hubId, summaryFields)
  }

  // ~~~ linked meta-data

  def linkedUserCollections: Seq[String] = links.filter(_.linkType == Link.LinkType.PARTOF).map(_.value(USERCOLLECTION_ID))

}

object MetadataRecord {

  def getMDR(hubId: String): Option[MetadataRecord] = {
    val Array(orgId, spec, recordId) = hubId.split("_")
    val collectionName = DataSet.getRecordsCollectionName(orgId, spec)
    getMDR(collectionName, hubId)
  }

  def getMDR(hubCollection: String, hubId: String) = connection(hubCollection).findOne(MongoDBObject(MDR_HUB_ID -> hubId)).map(grater[MetadataRecord].asObject(_))

  def getMDRs(hubCollection: String, hubIds: Seq[String]): List[MetadataRecord] = connection(hubCollection).find(MDR_HUB_ID $in hubIds).map(grater[MetadataRecord].asObject(_)).toList

  def getMDR(hubId: String, metadataFormat: String, accessKey: Option[String]): Option[MetadataRecord] = {
    if (hubId.split(":").length != 3)
      throw new InvalidIdentifierException("Invalid record identifier %s, should be of the form orgId:spec:localIdentifier".format(hubId))
    val Array(orgId, spec, localRecordKey) = hubId.split(":")
    val ds: Option[DataSet] = DataSet.findBySpecAndOrgId(spec, orgId)
    if (ds == None) return None

    // can we have access?
    if (!ds.get.formatAccessControl.get(metadataFormat).map(_.hasAccess(accessKey)).getOrElse(false)) {
      return None
    }

    val record: Option[MetadataRecord] = DataSet.getRecords(ds.get).findOne(MongoDBObject("localRecordKey" -> localRecordKey))
    if (record == None) {
      None
    } else if (record.get.rawMetadata.contains(metadataFormat)) {
      record
    } else {
      val mappedRecord = record.get
      val transformedDoc: String = transformDocument(metadataFormat, ds.get, mappedRecord)

      Some(mappedRecord.copy(rawMetadata = mappedRecord.rawMetadata.updated(metadataFormat, transformedDoc)))
    }
  }

  def transformDocument(prefix: String, dataSet: DataSet, record: MetadataRecord): String = {
    val mapping = dataSet.mappings.get(prefix)
    if (mapping == None) throw new MappingNotFoundException("Unable to find mapping for " + prefix)
    
    // FIXME - use dataSet.namespaces
    MappingService.transformRecord(record.getRawXmlString, mapping.get.recordMapping.getOrElse(""), mapping.get.format.allNamespaces.map(ns => (ns.prefix -> ns.uri)).toMap[String, String])
  }

  def getAccessors(orgIdSpec: Tuple2[String, String], hubIds: String*): List[_ <: MetadataAccessors] = {
    val collectionName = DataSet.getRecordsCollectionName(orgIdSpec._1, orgIdSpec._2)
    getAccessors(collectionName, hubIds: _ *)
  }

  def getAccessors(hubCollection: String, hubIds: String*): List[_ <: MetadataAccessors] = {
    val mdrs: Iterator[MetadataRecord] = connection(hubCollection).find(MDR_HUB_ID $in hubIds).map(grater[MetadataRecord].asObject(_))
    mdrs.map(mdr =>
      if (mdr.rawMetadata.size > 1) {
        Some(mdr.getDefaultAccessor)
      } else {
        None
      }).toList.flatten
  }


}

class SummaryFieldsMapMetadataAccessors(hubId: String, dbo: Map[String, String]) extends MetadataAccessors {
  protected def assign(key: String) = {
    dbo.get(key) match {
      case Some(v) => v
      case None => ""
    }
  }

  override def getHubId = hubId

  override def getRecordType = _root_.util.Constants.MDR
}

trait MDRCollection {
  self: SalatDAO[MetadataRecord, ObjectId] =>

  def existsByLocalRecordKey(key: String) = {
    count(MongoDBObject("localRecordKey" -> key)) > 0
  }

  def findByLocalRecordKey(key: String) = {
    findOne(MongoDBObject("localRecordKey" -> key))
  }

  def upsertByLocalKey(updatedRecord: MetadataRecord) {
    update(MongoDBObject("localRecordKey" -> updatedRecord.localRecordKey), updatedRecord, true, false, new WriteConcern())
  }
}

