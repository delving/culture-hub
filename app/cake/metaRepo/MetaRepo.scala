package cake.metaRepo

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/7/11 8:43 AM  
 */

trait MetaRepo {

  import java.util.Date
  import cake.metaRepo.PmhVerbType.PmhVerb

  def createDataSet(spec: String): DataSet

  def getDataSets: Iterable[_ <: DataSet]

  def getDataSet(spec: String): DataSet

  def getDataSetForIndexing(maxSimultaneous: Int): DataSet

  def getMetadataFormats: Set[_ <: MetadataFormat]

  def getMetadataFormats(id: String, accessKey: String): Set[_ <: MetadataFormat]

  def getFirstHarvestStep(verb: PmhVerb, set: String, from: Date, until: Date, metadataPrefix: String, accessKey: String): HarvestStep

  def getHarvestStep(resumptionToken: String, accessKey: String): HarvestStep

  def removeExpiredHarvestSteps: Unit

  def getRecord(identifier: String, metadataFormat: String, accessKey: String): Record

  def getMetaRepoConfig: MetaConfig

  final val RECORD_COLLECTION_PREFIX: String = "Records."
  final val DATASETS_COLLECTION: String = "Datasets"
  final val HARVEST_STEPS_COLLECTION: String = "HarvestSteps"
  final val MONGO_ID: String = "_id"
}

object Details {
  final val NAME: String = "name"
  final val METADATA_FORMAT: String = "metadata_format"
  final val FACT_BYTES: String = "fact_bytes"
  final val TOTAL_RECORDS: String = "total_records"
  final val DELETED_RECORDS: String = "deleted_records"
  final val UPLOADED_RECORDS: String = "uploaded_records"
}

abstract trait Details {
  def getName: String

  def setName(value: String): Unit

  def getMetadataFormat: MetadataFormat

  def getFacts: Array[Byte]

  def setFacts(factBytes: Array[Byte]): Unit

  def getTotalRecordCount: Int

  def setTotalRecordCount(count: Int): Unit

  def getDeletedRecordCount: Int

  def setDeletedRecordCount(count: Int): Unit

  def getUploadedRecordCount: Int

  def setUploadedRecordCount(count: Int): Unit
}

object HarvestStep {
  final val FIRST: String = "first"
  final val EXPIRATION: String = "exporatopm"
  final val LIST_SIZE: String = "listSize"
  final val CURSOR: String = "cursor"
  final val PMH_REQUEST: String = "pmhRequest"
  final val NAMESPACES: String = "namespaces"
  final val ERROR_MESSAGE: String = "error"
  final val AFTER_ID: String = "afterId"
  final val NEXT_ID: String = "nextId"
}

abstract trait HarvestStep {

  import org.bson.types.ObjectId
  import java.util.Date
  import com.mongodb.DBObject

  def getId: ObjectId

  def getExpiration: Date

  def getListSize: Int

  def createRecordFetcher(dataSet: DataSet, key: String): Runnable

  def getCursor: Int

  def getRecordCount: Int

  def getRecords: List[_ <: Record]

  def getPmhRequest: PmhRequest

  def getNamespaces: DBObject

  def hasNext: Boolean

  def nextResumptionToken: String

  def getAfterId: ObjectId

  def getNextId: ObjectId

  def getErrorMessage: String

  def save: Unit
}

object PmhRequest {
  final val VERB: String = "verb"
  final val SET: String = "set"
  final val FROM: String = "from"
  final val UNTIL: String = "until"
  final val PREFIX: String = "prefix"
}

abstract trait PmhRequest {

  import java.util.Date
  import cake.metaRepo.PmhVerbType.PmhVerb

  def getVerb: PmhVerb

  def getSet: String

  def getFrom: Date

  def getUntil: Date

  def getMetadataPrefix: String
}

object Record {
  final val MODIFIED: String = "modified"
  final val DELETED: String = "deleted"
  final val UNIQUE: String = "uniq"
  final val HASH: String = "hash"
}

abstract trait Record {

  import com.mongodb.DBObject
  import java.util.Date
  import org.bson.types.ObjectId

  def getId: ObjectId

  def getUnique: String

  def getModifiedDate: Date

  def isDeleted: Boolean

  def getNamespaces: DBObject

  def getHash: DBObject

  def getFingerprint: Map[String, Integer]

  def getXmlString: String

  def getXmlString(metadataPrefix: String): String
}

object Mapping {
  val RECORD_MAPPING: String = "recordMapping"
  val FORMAT: String = "format"
}

abstract trait Mapping {

  import eu.delving.metadata.RecordMapping

  def getMetadataFormat: MetadataFormat

  def getRecordMapping: RecordMapping
}

object MetadataFormat {
  val PREFIX: String = "prefix"
  val SCHEMA: String = "schema"
  val NAMESPACE: String = "namespace"
  val ACCESS_KEY_REQUIRED: String = "accessKeyRequired"
}

abstract trait MetaConfig {
  def getRepositoryName: String

  def getAdminEmail: String

  def getEarliestDateStamp: String

  def getRepositoryIdentifier: String

  def getSampleIdentifier: String
}

abstract trait MetadataFormat {
  def getPrefix: String

  def setPrefix(value: String): Unit

  def getSchema: String

  def setSchema(value: String): Unit

  def getNamespace: String

  def setNamespace(value: String): Unit

  def isAccessKeyRequired: Boolean

  def setAccessKeyRequired(required: Boolean): Unit
}

abstract trait DataSet {

  import com.mongodb.DBObject
  import eu.delving.sip.DataSetState
  import java.util.Date
  import java.io.InputStream
  import eu.delving.metadata.RecordMapping
  import org.bson.types.ObjectId

  def getSpec: String

  def hasDetails: Boolean

  def createDetails: Details

  def getDetails: Details

  def setFactsHash(sourceHash: String): Unit

  def getNamespaces: DBObject

  def getState(fresh: Boolean): DataSetState

  def getErrorMessage: String

  def setState(dataSetState: DataSetState): Unit

  def setErrorState(message: String): Unit

  def parseRecords(inputStream: InputStream): Unit

  def setSourceHash(hash: String, downloaded: Boolean): Unit

  def setMapping(recordMapping: RecordMapping, accessKeyRequired: Boolean): Unit

  def setMappingHash(metadataPrefix: String, hash: String): Unit

  def getRecordsIndexed: Int

  def setRecordsIndexed(count: Int): Unit

  def incrementRecordsIndexed(increment: Int): Unit

  def mappings: Map[String, Mapping]

  def getRecordCount: Int

  def getRecord(id: ObjectId, metadataPrefix: String, accessKey: String): Record

  def getRecords(prefix: String, count: Int, from: Date, afterId: ObjectId, until: Date, accessKey: String): DataSet#RecordFetch

  def getHashes: List[String]

  def save: Unit

  def delete: Unit

  abstract trait RecordFetch {
    def getRecords: List[_ <: Record]

    def getAfterId: ObjectId
  }
}

object PmhVerbType extends Enumeration {

        case class PmhVerb(command:String) extends Val(command)
        val LIST_SETS = PmhVerb("ListSets")
        val List_METADATA_FORMATS = PmhVerb("ListMetadataFormats")
        val LIST_IDENTIFIERS = PmhVerb("ListIdentifiers")
        val LIST_RECORDS = PmhVerb("ListRecords")
        val GET_RECORD = PmhVerb("GetRecord")
        val IDENTIFY = PmhVerb("Identify")
}