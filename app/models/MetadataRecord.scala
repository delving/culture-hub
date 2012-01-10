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
import org.bson.types.ObjectId
import java.util.Date
import exceptions.RecordNotFoundException
import models.salatContext._
import com.novus.salat.grater
import util.Constants._
import controllers.MetadataAccessors
import com.novus.salat.dao.SalatDAO
import com.mongodb.{WriteConcern, BasicDBList, DBObject}

case class MetadataRecord(_id: ObjectId = new ObjectId,
                          hubId: String,
                          rawMetadata: Map[String, String], // this is the raw xml data string
                          mappedMetadata: Map[String, DBObject] = Map.empty[String, DBObject], // this is the mapped xml data string only added after transformation, and it's a DBObject because Salat won't let us use an inner Map[String, List[String]]
                          modified: Date = new Date(),
                          validOutputFormats: List[String] = List.empty[String], // valid formats this records can be mapped to
                          deleted: Boolean = false, // if the record has been deleted
                          transferIdx: Option[Int] = None, // 0-based index for the transfer order
                          localRecordKey: String, // the unique element value
                          links: List[EmbeddedLink] = List.empty[EmbeddedLink],
                          globalHash: String, // the hash of the raw content
                          hash: Map[String, String] // the hash for each field, for duplicate detection
                           ) {

  def getUri(orgId: String, spec: String) = "http://%s/%s/object/%s/%s".format(getNode, orgId, spec, localRecordKey)

  def getXmlString(metadataPrefix: String = "raw"): String = {
    if (rawMetadata.contains(metadataPrefix)) {
      rawMetadata.get(metadataPrefix).get
    }
    else if (mappedMetadata.contains(metadataPrefix)) {
      import scala.collection.JavaConversions._
      val indexDocument: MongoDBObject = mappedMetadata.get(metadataPrefix).get
      indexDocument.entrySet().foldLeft("")(
        (output, indexDoc) => {
          val unMungedKey = indexDoc.getKey.replaceFirst("_", ":")
          output + indexDoc.getValue.asInstanceOf[List[String]].map(value => {
            "<%s>%s</%s>".format(unMungedKey, value.toString, unMungedKey)
          }).mkString
        }
      )
    }
    else
      throw new RecordNotFoundException("Unable to find record with source metadata prefix: %s".format(metadataPrefix))
  }

  def getXmlStringAsRecord(metadataPrefix: String = "raw"): String = {
    "<record>%s</record>".format(getXmlString(metadataPrefix))
  }

  def getDefaultAccessor = {
    val (prefix, map) = mappedMetadata.head
    new MultiValueMapMetadataAccessors(hubId, map)
  }

  def getAccessor(prefix: String) = {
    if (!mappedMetadata.contains(prefix)) new MultiValueMapMetadataAccessors(hubId, MongoDBObject())
    val map = mappedMetadata(prefix)
    new MultiValueMapMetadataAccessors(hubId, map)
  }

}

object MetadataRecord {

  def getMDR(hubId: String): Option[MetadataRecord] = {
    val Array(orgId, spec, recordId) = hubId.split("_")
    val collectionName = DataSet.getRecordsCollectionName(orgId, spec)
    connection(collectionName).findOne(MongoDBObject(MDR_HUB_ID -> hubId)) match {
      case Some(dbo) => Some(grater[MetadataRecord].asObject(dbo))
      case None => None
    }
  }

}

class MultiValueMapMetadataAccessors(hubId: String, dbo: MongoDBObject) extends MetadataAccessors {
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

