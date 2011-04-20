package eu.delving.model

import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord, MongoId}
import net.liftweb.mongodb.record.field.{DateField, MongoCaseClassListField, MongoCaseClassField, MongoMapField}
import net.liftweb.record.field.{BooleanField, IntField, StringField}
import java.util.concurrent.ConcurrentHashMap
import net.liftweb.mongodb.{JsonObjectMeta, JsonObject}

/**
 * MongoDB storage for metadata repository
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

class DataSet private() extends MongoRecord[DataSet] with MongoId[DataSet] {

  def meta = DataSet

  object spec extends StringField(this, "")

  object state extends MongoCaseClassField[DataSet, DataSetState](this)

  object namespaces extends MongoMapField[DataSet, String](this)

  object mappings extends MongoCaseClassListField[DataSet, Mapping](this)

  object sourceHash extends StringField(this, "")

  object downloadedSourceHash extends StringField(this, "")

  object recordsIndexed extends IntField(this, -1)

  object details extends MongoCaseClassField[DataSet, Details](this)

  object errorMessage extends StringField(this, "")
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

object MetadataRepository