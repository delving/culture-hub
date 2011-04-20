package eu.delving.model

import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord, MongoId}
import net.liftweb.mongodb.{JsonObjectMeta, JsonObject}
import net.liftweb.mongodb.record.field.{DateField, MongoCaseClassListField, MongoCaseClassField, MongoMapField}
import net.liftweb.record.field.{BooleanField, IntField, StringField}

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

object DataSet extends DataSet with MongoMetaRecord[DataSet]

class DataRecord private() extends MongoRecord[DataRecord] with MongoId[DataRecord] {
  def meta = DataRecord

  object unique extends StringField(this, "")

  object modified extends DateField(this)

  object deleted extends BooleanField(this)

  object namespaces extends MongoMapField[DataRecord, String](this)

  object hashes extends MongoMapField[DataRecord, String](this)

  object xml extends StringField(this, "")
}

object DataRecord extends DataRecord with MongoMetaRecord[DataRecord]

case class DataSetState(name : String) extends JsonObject[DataSetState] {
  def meta = DataSetState

  val Incomplete = DataSetState("Incomplete")
  val Disabled = DataSetState("Disabled")
  val Uploaded = DataSetState("Uploaded")
  val Queued = DataSetState("Queued")
  val Indexing = DataSetState("Indexing")
  val Enabled = DataSetState("Enabled")
  val Error = DataSetState("Error")
}

object DataSetState extends JsonObjectMeta[DataSetState]

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

class MetadataRepository