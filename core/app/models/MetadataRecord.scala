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
import exceptions.{MappingNotFoundException, InvalidIdentifierException}
import org.bson.types.ObjectId
import java.util.Date
import models.mongoContext._
import com.novus.salat.grater
import com.novus.salat.dao.SalatDAO
import com.mongodb.WriteConcern
import core.mapping.MappingService
import core.Constants._

case class MetadataRecord(_id: ObjectId = new ObjectId,
                          hubId: String,
                          rawMetadata: Map[String, String], // this is the raw xml data string, in various mapped formats and in origin (as "raw")
                          systemFields: Map[String, List[String]] = Map.empty, // the map containing the system fields for this record, if available
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

  def getCachedTransformedRecord(metadataPrefix: String): Option[String] = rawMetadata.get(metadataPrefix)

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
    if (hubId.split("_").length < 3)
      throw new InvalidIdentifierException("Invalid record identifier %s, should be of the form orgId_spec_localIdentifier".format(hubId))
    val idParts = hubId.split("_")
    val spec = idParts(1)
    val orgId = idParts(0)
    val localRecordKey = idParts.drop(2).mkString("_")

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

    MappingService.transformRecord(record.getRawXmlString, mapping.get.recordMapping.getOrElse(""), dataSet.namespaces)
  }

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

