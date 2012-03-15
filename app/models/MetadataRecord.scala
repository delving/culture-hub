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
import com.mongodb.{WriteConcern, BasicDBList}
import core.search.{SolrBindingService}
import core.mapping.MappingService

case class MetadataRecord(_id: ObjectId = new ObjectId,
                          hubId: String,
                          rawMetadata: Map[String, String], // this is the raw xml data string
                          mappedMetadata: Map[String, Map[String, List[String]]] = Map.empty,
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
    } else if (mappedMetadata.contains(metadataPrefix)) {

      // welcome to the mad world of working around some mongo/casbah/salat limitation

      // we store the mappings in a Map[String, Map[String, List[String]]]
      // so for one prefix we should get back a Map[String, List[String]]
      // but instead we get back a Map[String, BasicDBList]
      // basically the first value of each list is the key, the second one the value (which is itself a BasicDBList, where the values are strings)
      // thus the code below. don't touch.

      import scala.collection.JavaConverters._

      val map = mappedMetadata.asInstanceOf[Map[String, BasicDBList]]
      val inner = map(metadataPrefix).asScala.map(entry => entry.asInstanceOf[BasicDBList])

      inner.foldLeft("") {
        (output: String, e: BasicDBList) => {
          val entry = (e.get(0), e.get(1))
          val unMungedKey = SolrBindingService.stripDynamicFieldLabels(entry._1.toString.replaceFirst("_", ":"))
          val value = entry._2.asInstanceOf[BasicDBList].toList
          if (!unMungedKey.startsWith("delving"))
            output + value.map(v => "<%s>%s</%s>".format(unMungedKey, v, unMungedKey)).mkString("", "\n", "\n")
          else
            output
        }
      }
    } else {
      throw new RecordNotFoundException("Unable to find record with source metadata prefix: %s".format(metadataPrefix))
    }
  }

  def getDefaultAccessor = {
    val (prefix, map) = mappedMetadata.head
    new MultiValueMapMetadataAccessors(hubId, map)
  }

  def getAccessor(prefix: String) = {
    if (!mappedMetadata.contains(prefix)) new MultiValueMapMetadataAccessors(hubId, Map())
    val map = mappedMetadata(prefix)
    new MultiValueMapMetadataAccessors(hubId, map)
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
      val transformedDoc: Map[String, List[String]] = transformXml(metadataFormat, ds.get, mappedRecord)

      Some(mappedRecord.copy(mappedMetadata = mappedRecord.mappedMetadata.updated(metadataFormat, transformedDoc)))
    }
  }

  def transformXml(prefix: String, dataSet: DataSet, record: MetadataRecord): Map[String, List[String]] = {
    val mapping = dataSet.mappings.get(prefix)
    if (mapping == None) throw new MappingNotFoundException("Unable to find mapping for " + prefix)
    MappingService.transformXml(record.getRawXmlString, mapping.get.recordMapping.getOrElse(""), dataSet.namespaces)
  }

  def getAccessors(orgIdSpec: Tuple2[String, String], hubIds: String*): List[_ <: MetadataAccessors] = {
    val collectionName = DataSet.getRecordsCollectionName(orgIdSpec._1, orgIdSpec._2)
    getAccessors(collectionName, hubIds: _ *)
  }

  def getAccessors(hubCollection: String, hubIds: String*): List[_ <: MetadataAccessors] = {
    val mdrs: Iterator[MetadataRecord] = connection(hubCollection).find(MDR_HUB_ID $in hubIds).map(grater[MetadataRecord].asObject(_))
    mdrs.map(mdr =>
      if (!mdr.mappedMetadata.isEmpty) {
        Some(mdr.getDefaultAccessor)
      } else {
        None
      }).toList.flatten
  }

}

class MultiValueMapMetadataAccessors(hubId: String, dbo: Map[String, List[String]]) extends MetadataAccessors {
  protected def assign(key: String) = {
    dbo.get(key) match {
      case Some(v) => v.asInstanceOf[BasicDBList].toList.head.toString
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

