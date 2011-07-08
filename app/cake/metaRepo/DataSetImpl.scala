package cake.metaRepo

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/7/11 6:48 PM  
 */

class DataSetImpl extends DataSet {

  import java.util.Date
  import org.bson.types.ObjectId
  import eu.delving.metadata.RecordMapping
  import java.io.InputStream
  import eu.delving.sip.DataSetState
  import com.mongodb.DBObject
  import models.Details

  def getSpec: String = null

  def hasDetails: Boolean = false

  def createDetails: Details = null

  def getDetails: Details = null

  def setFactsHash(sourceHash: String) {}

  def getNamespaces: DBObject = null

  def getState(fresh: Boolean): DataSetState = null

  def getErrorMessage: String = null

  def setState(dataSetState: DataSetState) {}

  def setErrorState(message: String) {}

  def parseRecords(inputStream: InputStream) {}

  def setSourceHash(hash: String, downloaded: Boolean) {}

  def setMapping(recordMapping: RecordMapping, accessKeyRequired: Boolean) {}

  def setMappingHash(metadataPrefix: String, hash: String) {}

  def getRecordsIndexed: Int = 0

  def setRecordsIndexed(count: Int) {}

  def incrementRecordsIndexed(increment: Int) {}

  def mappings: Map[String, Mapping] = null

  def getRecordCount: Int = 0

  def getRecord(id: ObjectId, metadataPrefix: String, accessKey: String): Record = null

  def getRecords(prefix: String, count: Int, from: Date, afterId: ObjectId, until: Date, accessKey: String): DataSet#RecordFetch = null

  def getHashes: List[String] = null

  def save {}

  def delete {}
}