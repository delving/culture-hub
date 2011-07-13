package cake.metaRepo

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/7/11 9:27 AM  
 */

class MetaRepoImpl {

  import cake.metaRepo.PmhVerbType.PmhVerb
  import java.util.Date
  import models.DataSet

//  def createDataSet(spec: String): DataSet = null
//
//  def getDataSets: Iterable[_ <: DataSet] = null
//
//  def getDataSet(spec: String): DataSet = null
//
//  def getDataSetForIndexing(maxSimultaneous: Int): DataSet = null
//
//  def getMetadataFormats: Set[_ <: MetadataFormat] = null
//
//  def getMetadataFormats(id: String, accessKey: String): Set[_ <: MetadataFormat] = null
//
//  def getFirstHarvestStep(verb: PmhVerb, set: String, from: Date, until: Date, metadataPrefix: String, accessKey: String): HarvestStep = null
//
//  def getHarvestStep(resumptionToken: String, accessKey: String): HarvestStep = null
//
  def removeExpiredHarvestSteps {}
//
//  def getRecord(identifier: String, metadataFormat: String, accessKey: String): MetadataRecord = null
}

class AccessKeyException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class BadArgumentException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class DataSetNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class HarvindexingException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class MappingNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class MetaRepoSystemException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class RecordParseException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}

class ResumptionTokenNotFoundException(s: String, throwable: Throwable) extends Exception(s, throwable) {
  def this(s: String) = this(s, null)
}