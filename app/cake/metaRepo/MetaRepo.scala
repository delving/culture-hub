package cake.metaRepo

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/7/11 8:43 AM  
 */

// Implemented
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

//  def getMetaRepoConfig: MetaConfig
}

// implemented
trait HarvestStep {

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

// implemented
trait PmhRequest {

  import java.util.Date
  import cake.metaRepo.PmhVerbType.PmhVerb

  def getVerb: PmhVerb

  def getSet: String

  def getFrom: Date

  def getUntil: Date

  def getMetadataPrefix: String
}

// implemented
trait Record {

  import com.mongodb.DBObject
  import java.util.Date
  import org.bson.types.ObjectId

  def getId: ObjectId

  def getUnique: String

  def getModifiedDate: Date

  def isDeleted: Boolean

  def getNamespaces: DBObject

  def getHash: DBObject

  def getFingerprint: Map[String, Int]

  def getXmlString: String

  def getXmlString(metadataPrefix: String): String
}

// implemented  can be deprecated
trait Mapping {

  import eu.delving.metadata.RecordMapping

  def getMetadataFormat: MetadataFormat

  def getRecordMapping: RecordMapping
}

// implemented can be deprecated
trait MetadataFormat {
  def getPrefix: String

  def setPrefix(value: String): Unit

  def getSchema: String

  def setSchema(value: String): Unit

  def getNamespace: String

  def setNamespace(value: String): Unit

  def isAccessKeyRequired: Boolean

  def setAccessKeyRequired(required: Boolean): Unit
}

trait DataSet {

  import com.mongodb.DBObject
  import eu.delving.sip.DataSetState
  import java.util.Date
  import java.io.InputStream
  import eu.delving.metadata.RecordMapping
  import org.bson.types.ObjectId
  import models.Details

  def getSpec: String

  def hasDetails: Boolean

  def createDetails: Details

  def getDetails: Details

  def setFactsHash(sourceHash: String): Unit

  def getNamespaces: DBObject

  def getState(fresh: Boolean): DataSetState

  def setState(dataSetState: DataSetState): Unit

  def getErrorMessage: String

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

// from here complete

object PmhVerbType extends Enumeration {

  case class PmhVerb(command: String) extends Val(command)

  val LIST_SETS = PmhVerb("ListSets")
  val List_METADATA_FORMATS = PmhVerb("ListMetadataFormats")
  val LIST_IDENTIFIERS = PmhVerb("ListIdentifiers")
  val LIST_RECORDS = PmhVerb("ListRecords")
  val GET_RECORD = PmhVerb("GetRecord")
  val IDENTIFY = PmhVerb("Identify")
}

trait MetaConfig {

  import play.Play
  def conf(key: String) = Play.configuration.getProperty(key).trim

  val repositoryName: String = conf("services.pmh.repositoryName ")
  val adminEmail: String = conf("services.pmh.adminEmail")
  val earliestDateStamp: String = conf("services.pmh.earliestDateStamp")
  val repositoryIdentifier: String = conf("services.pmh.repositoryIdentifier")
  val sampleIdentifier: String = conf("services.pmh.sampleIdentifier")
}