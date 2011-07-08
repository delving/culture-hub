package models

import java.util.Date
import cake.metaRepo.PmhVerbType.PmhVerb
import org.bson.types.ObjectId
import eu.delving.metadata.RecordMapping
import eu.delving.sip.DataSetState

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/8/11 8:12 AM  
 */

case class Mapping(recordMapping: RecordMapping,
                   format: MetadataFormat,
                   mapping_hash: Array[Byte],
                   rec_indexed: Int,
                   indexed: Boolean)

case class MetadataFormat (prefix: String,
                           schema: String,
                           namespace: String,
                           accessKeyRequired: Boolean) //  extends MetadataFormat

case class Details(
                          name: String,
                          uploaded_records: Int = 0,
                          total_records: Int = 0,
                          deleted_records: Int = 0,
                          metadataFormat: MetadataFormat,
                          facts_bytes: Array[Byte]
                          ) //extends Details  {} // Details doesn't have to be implemented


case class DataSet(
                          spec: String,
                          state: DataSetState, // imported from sip-core
                          details: Details,
                          facts_hash: Array[Byte],
                          source_hash: Array[Byte],
                          namespaces: Map[String, String],
                          mappings: Map[String, Mapping]
                          ) {}


case class Record (
                  metadata: Map[String, String], // this is the raw xml data string
                  modified: Date,
                  deleted: Boolean, // if the record has been deleted
                  uniq: String,
                  hash: Map[String, String]) //extends Record
{
//  import org.bson.types.ObjectId
//  import com.mongodb.DBObject
//
//  def getId: ObjectId
//
//  def getUnique: String
//
//  def getModifiedDate: Date
//
//  def isDeleted: Boolean
//
//  def getNamespaces: DBObject
//
//  def getHash: DBObject
//
//  def getFingerprint: Map[String, Integer]

  def getXmlString(metadataPrefix: String = "raw"): String = {
    metadata.get(metadataPrefix).getOrElse("<dc:title>nothing<dc:title>")
    // todo maybe give back as Elem and check for validity
  }
}

case class PmhRequest(
  verb: PmhVerb,
  set: String,
  from: Option[Date],
  until: Option[Date],
  prefix: String
) // extends PmhRequest
{
  def getVerb: PmhVerb = verb

  def getSet: String = set

  def getFrom: Option[Date] = from

  def getUntil: Option[Date] = until

  def getMetadataPrefix: String = prefix
}

case class HarvestStep (
                               first: String,  //   not sure if this should be in
                               exporatopm: Date, //
                               listSize: Int,
                               cursor: Int,
                               pmhRequest: PmhRequest,
                               namespaces: Map[String, String],
                               error: String,
                               afterId: ObjectId,
                               nextId: ObjectId
) {}

