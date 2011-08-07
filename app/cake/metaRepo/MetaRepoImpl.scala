package cake.metaRepo

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/7/11 9:27 AM  
 */

// todo delete this class

class MetaRepoImpl {

  import java.util.Date
  import models.{MetadataRecord, DataSet}
  import cake.metaRepo.PmhVerbType.PmhVerb

  def createDataSet(spec: String): DataSet = null

  def getDataSets: Iterable[_ <: DataSet] = null

  def getDataSet(spec: String): DataSet = null

  def getDataSetForIndexing(maxSimultaneous: Int): DataSet = null

  def getMetadataFormats: Set[_ <: MetadataFormat] = null

  def getMetadataFormats(id: String, accessKey: String): Set[_ <: MetadataFormat] = null

  def getFirstHarvestStep(verb: PmhVerb, set: String, from: Date, until: Date, metadataPrefix: String, accessKey: String): HarvestStep = null

  def getHarvestStep(resumptionToken: String, accessKey: String): HarvestStep = null

  def removeExpiredHarvestSteps {}

  def getRecord(identifier: String, metadataFormat: String, accessKey: String): MetadataRecord = null
}