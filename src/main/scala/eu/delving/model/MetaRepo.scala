package eu.delving.model

import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord, MongoId}
import net.liftweb.record.field.{BooleanField, IntField, StringField}
import java.util.concurrent.ConcurrentHashMap
import net.liftweb.mongodb.{JsonObjectMeta, JsonObject}
import net.liftweb.mongodb.record.field._
import java.util.Date
import net.liftweb.common.{Empty, Box}

/**
 * MongoDB storage for metadata repository
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

class DataSet private() extends MongoRecord[DataSet] with MongoId[DataSet] {

  def meta = DataSet

  object spec extends StringField(this, "")
  object state extends MongoCaseClassField[DataSet, DataSetState](this)
  object facts extends MongoCaseClassField[DataSet, Facts](this)
  object namespaces extends MongoMapField[DataSet, String](this)
  object mappings extends MongoCaseClassListField[DataSet, Mapping](this)
  object sourceHash extends StringField(this, "")
  object downloadedSourceHash extends StringField(this, "")
  object recordsIndexed extends IntField(this, -1)
  object details extends MongoCaseClassField[DataSet, Details](this)
  object errorMessage extends StringField(this, "")

  def hashes: List[String] = {
    var hashes = facts.get.hash :: sourceHash.get :: downloadedSourceHash.get :: mappings.get.map(_.hash)
    hashes.filterNot(_.isEmpty)
  }

  def records: DataRecordMeta = DataRecordCollection(spec.get)
}

object DataSet extends DataSet with MongoMetaRecord[DataSet] {
  override def collectionName = "dataset"
}

abstract class DataRecord() extends MongoRecord[DataRecord] with MongoId[DataRecord] {

  object unique extends StringField(this, "")
  object modified extends DateField(this)
  object deleted extends BooleanField(this)
  object namespaces extends MongoMapField[DataRecord, String](this)
  object hashes extends MongoMapField[DataRecord, String](this)
  object xml extends StringField(this, "")
}

abstract class DataRecordMeta extends DataRecord with MongoMetaRecord[DataRecord]

object DataRecordCollection {
  private val metaObjects = new ConcurrentHashMap[String, DataRecordMeta]

  def apply(spec: String): DataRecordMeta = {
    val existingMeta = metaObjects.get(spec)
    if (existingMeta != null) {
      existingMeta
    }
    else {
      val freshMeta = new DataRecordMeta {
        self: DataRecordMeta =>

        override def meta = self

        override def collectionName = "records_" + spec

        override protected def instantiateRecord = new DataRecord {
          override def meta = self
        }
      }
      metaObjects.put(spec, freshMeta)
      freshMeta
    }
  }
}

class HarvestStep private() extends MongoRecord[HarvestStep] with MongoId[HarvestStep] {
  val meta = HarvestStep

  object pmhRequest extends MongoCaseClassField[HarvestStep, PmhRequest](this)
  object expiration extends DateField(this)
  object listSize extends IntField(this, -1)
  object cursor extends IntField(this, -1)
  object recordCount extends IntField(this, -1)
  object namespaces extends MongoMapField[HarvestStep, String](this)
  object afterRecord extends ObjectIdField[HarvestStep](this)
  object nextHarvestStep extends ObjectIdField[HarvestStep](this)
  object errorMessage extends StringField(this, "")
}

object HarvestStep extends HarvestStep with MongoMetaRecord[HarvestStep] {
  override def collectionName = "harvest_step"
}

case class DataSetState(name : String) extends JsonObject[DataSetState] {
  def meta = DataSetState
}

object DataSetState extends JsonObjectMeta[DataSetState] {
  val Incomplete = DataSetState("Incomplete")
  val Disabled = DataSetState("Disabled")
  val Uploaded = DataSetState("Uploaded")
  val Queued = DataSetState("Queued")
  val Indexing = DataSetState("Indexing")
  val Enabled = DataSetState("Enabled")
  val Error = DataSetState("Error")
}

case class Facts(
  factBytes: Array[Byte],
  hash: String
)
extends JsonObject[Facts] {
  def meta = Facts
}

object Facts extends JsonObjectMeta[Facts]

case class Details(
  name: String,
  description: String,
  totalRecords: Int,
  deletedRecords: Int,
  uploadedRecords: Int,
  factBytes: Array[Byte],
  metadataFormat: MetadataFormat
)
extends JsonObject[Details] {
  def meta = Details
}

object Details extends JsonObjectMeta[Details]

case class MetadataFormat(
  prefix: String,
  schema: String,
  namespaceUri: String,
  accessKeyRequired: Boolean
)
extends JsonObject[MetadataFormat] {
  def meta = MetadataFormat
}

object MetadataFormat extends JsonObjectMeta[MetadataFormat]

case class Mapping(
  prefix: String,
  code: String,
  hash: String
)
extends JsonObject[Mapping] {
  def meta = Mapping
}

object Mapping extends JsonObjectMeta[Mapping]

case class PmhRequest (
  verb: String,
  set: String,
  from: Date,
  until: Date,
  prefix : String
)
extends JsonObject[PmhRequest] {
  def meta = PmhRequest
}

object PmhRequest extends JsonObjectMeta[PmhRequest]

object MetaRepo {

  def createDataSet(spec: String) : DataSet = {
    DataSet.createRecord.spec(spec).save
  }

  def dataSet(spec: String) : Box[DataSet] = {
    DataSet.find("spec", spec)
  }

  def dataSet(state : DataSetState): Box[DataSet] = {
    DataSet.find("state", state)
  }

  def dataRecord(identifier: String, prefix: String) : Box[DataRecord] = {
    Empty
  }

  def metadataFormats: List[MetadataFormat] = {
    Nil
  }

  def firstHarvestStep(verb: String, spec: String, prefix: String, from: Option[Date], until: Option[Date]): Box[HarvestStep] = {
    Empty
  }

  def harvestStep(resumptionToken: String): Box[HarvestStep] = {
    Empty
  }

  def removeExpiredHarvestSteps() {
  }

}