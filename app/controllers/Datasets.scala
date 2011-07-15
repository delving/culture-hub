package controllers

import play.mvc.Controller

/**
 * This Controller is responsible for all the interaction with the SIP-Creator
 *
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/7/11 12:04 AM  
 */

object Datasets extends Controller {

  import play.mvc.results.Result
  import org.apache.solr.client.solrj.SolrServer
  import play.mvc.Http
  import java.io.{OutputStream, InputStream}
  import java.util.zip.ZipOutputStream
  import eu.delving.metadata.{Facts, RecordMapping, MetadataModel}
  import eu.delving.sip.{DataSetInfo, DataSetResponse, DataSetResponseCode, AccessKey}
  import play.mvc.results.RenderText
  import play.mvc.results.RenderXml
  import org.apache.log4j.Logger
  import cake.ComponentRegistry
  import models.DataSet
  import xml.Elem

  private val RECORD_STREAM_CHUNK: Int = 1000
  private val log: Logger = Logger.getLogger(getClass)

  private val metadataModel: MetadataModel = ComponentRegistry.metadataModel

  private val accessKeyService: AccessKey = ComponentRegistry.accessKey

  def secureListAll: Result = {
    try {
      renderDataSetListAsXml(dataSets = DataSet.findAll)
    }
    catch {
      case e: Exception => renderException(e)
    }
  }

  private def renderDataSetList(responseCode: DataSetResponseCode = DataSetResponseCode.THANK_YOU,
                        dataSets: List[DataSet] = List[DataSet](),
                        errorMessage: String = "") : Elem = {
    <data-set responseCode={responseCode.toString}>
      <data-set-list>
        {dataSets.map{ds => ds.toXml}}
      </data-set-list>
      {if (responseCode != DataSetResponseCode.THANK_YOU) {
          <errorMessage>{errorMessage}</errorMessage>
        }
      }
    </data-set>
  }

  private def renderDataSetListAsXml(responseCode: DataSetResponseCode = DataSetResponseCode.THANK_YOU,
                        dataSets: List[DataSet] = List.empty[DataSet],
                        errorMessage: String = "") : Result = {
    new RenderXml(renderDataSetList(responseCode, dataSets, errorMessage).toString)
  }

  def listAll(accessKey: String): Result = {
    try {
      import play.mvc.results.RenderXml
      checkAccessKey(accessKey)
      renderDataSetListAsXml(dataSets = DataSet.findAll)
    }
    catch {
      case e: Exception => renderException(e)
    }
  }

//  @RequestMapping(value = Array("/administrator/dataset/{dataSetSpec}/{command}"))
  def secureIndexingControl(dataSetSpec: String, command: String): Result = {
    indexingControlInternal(dataSetSpec, command)
  }

//  @RequestMapping(value = Array("/dataset/{dataSetSpec}/{command}")) 
  def indexingControl(dataSetSpec: String, command: String, accessKey: String): Result = {
    try {
      checkAccessKey(accessKey)
      indexingControlInternal(dataSetSpec, command)
    }
    catch {
      case e: Exception => renderException(e)
    }
  }

  private def checkAccessKey(accessKey: String) {
    import cake.metaRepo.AccessKeyException
    if (accessKey.isEmpty) {
      log.warn("Service Access Key missing")
      throw new AccessKeyException("Access Key missing")
    }
    else if (!accessKeyService.checkKey(accessKey)) {
      log.warn(String.format("Service Access Key %s invalid!", accessKey))
      throw new AccessKeyException(String.format("Access Key %s not accepted", accessKey))
    }
  }

//  @RequestMapping(value = Array("/dataset/submit/{dataSetSpec}/{fileType}/{fileName}"), method = Array(RequestMethod.POST))



  def acceptFile(dataSetSpec: String, fileType: String, fileName: String, accessKey: String): Result = {
    try {
      import play.mvc.results.RenderXml
      import eu.delving.metadata.Hasher
      import java.util.zip.GZIPInputStream
      checkAccessKey(accessKey)
      log.info(String.format("accept type %s for %s: %s", fileType, dataSetSpec, fileName))
      var hash: String = Hasher.extractHashFromFileName(fileName)
      if (hash == null) {
        throw new RuntimeException("No hash available for file name " + fileName)
      }
      val inputStream: InputStream = request.body
      val responseCode = fileType match {
        case "text/plain" | "FACTS" => receiveFacts(Facts.read(inputStream), dataSetSpec, hash)
        case "application/x-gzip" | "SOURCE"=> receiveSource(new GZIPInputStream(inputStream), dataSetSpec, hash)
        case "text/xml" | "MAPPING" => receiveMapping(RecordMapping.read(inputStream, metadataModel), dataSetSpec, hash)
        case _ => DataSetResponseCode.SYSTEM_ERROR
      }
      renderDataSetListAsXml(responseCode = responseCode)
    }
    catch {
      case e: Exception => renderException(e)
    }

  }

  private def receiveMapping(recordMapping: RecordMapping, dataSetSpec: String, hash: String): DataSetResponseCode = {
    import models.HarvestStep
    val dataSet: DataSet = DataSet.getWithSpec(dataSetSpec)
    if (dataSet.hasHash(hash)) {
      return DataSetResponseCode.GOT_IT_ALREADY
    }
    HarvestStep.removeFirstHarvestSteps(dataSetSpec)
    DataSet.save(dataSet.setMapping(mapping = recordMapping, hash = hash))
    DataSetResponseCode.THANK_YOU
  }



  private def receiveSource(inputStream: InputStream, dataSetSpec: String, hash: String): DataSetResponseCode = {
    val dataSet: DataSet = DataSet.getWithSpec(dataSetSpec)
    if (dataSet.hasHash(hash)) {
      return DataSetResponseCode.GOT_IT_ALREADY
    }
    DataSet.parseRecords(inputStream, dataSet)

    val recordCount: Int = DataSet.getRecordCount(dataSetSpec)

    val details = dataSet.details.copy(
      total_records = recordCount,
      deleted_records = recordCount - dataSet.details.uploaded_records
    )
    val ds = dataSet.copy(source_hash = hash, details = details)
    DataSet.save(ds)
    DataSetResponseCode.THANK_YOU
  }

  private def receiveFacts(facts: Facts, dataSetSpec: String, hash: String): DataSetResponseCode = {
    import models.{MetadataFormat, Details}
    import eu.delving.metadata.{MetadataException, MetadataNamespace}
    import cake.metaRepo.MetaRepoSystemException
    import com.mongodb.casbah.commons.MongoDBObject
    import com.mongodb.WriteConcern
    val dataSet: Option[DataSet] = DataSet.find(dataSetSpec)
    if (dataSet != None && dataSet.get.hasHash(hash)) {
      return DataSetResponseCode.GOT_IT_ALREADY
    }

    val prefix = facts.get("namespacePrefix")
    val ns = MetadataNamespace.values.filter(_.getPrefix == prefix).headOption.getOrElse(
      throw new MetaRepoSystemException("Unable to retrieve metadataFormat info for prefix: " + prefix)
    )

    val metadataFormat = MetadataFormat(prefix, ns.getSchema, ns.getUri, true)

    val details: Details = Details(
      name = facts.get("name"),
      uploaded_records = facts.getRecordCount.toInt,
      metadataFormat = metadataFormat,
      facts_bytes = Facts.toBytes(facts)
    )

    val updatedDataSet : DataSet = {
      import eu.delving.sip.DataSetState
      dataSet match {
        case None => DataSet(spec = dataSetSpec, state = DataSetState.INCOMPLETE.toString, details = details,
          facts_hash = hash)
        case _ => dataSet.get.copy(facts_hash = hash, details = details)
      }
    }
    DataSet.update(MongoDBObject("_id" -> updatedDataSet._id), updatedDataSet, true, false, new WriteConcern())

    DataSetResponseCode.THANK_YOU
  }

  private def indexingControlInternal(dataSetSpec: String, commandString: String): Result = {
    try {
      import eu.delving.sip.DataSetState._
      import eu.delving.sip.DataSetCommand._
      import eu.delving.sip.{DataSetState, DataSetCommand}
      import eu.delving.sip.DataSetState
      import eu.delving.sip.DataSetState._

      val dataSet: DataSet = DataSet.getWithSpec(dataSetSpec)

      val command: DataSetCommand = DataSetCommand.valueOf(commandString)
      val state: DataSetState = dataSet.getDataSetState

      def changeState(state: DataSetState): DataSet = {
        val mappings = dataSet.mappings.transform((key, map) => map.copy(rec_indexed = 0))
        val updatedDataSet = dataSet.copy(state = state.toString, mappings = mappings)
        DataSet.save(updatedDataSet)
        updatedDataSet
      }

      val response =  command match {
        case DISABLE =>
          state match {
            case QUEUED | INDEXING | ERROR | ENABLED =>
              val updatedDataSet = changeState(state = DataSetState.DISABLED)
              DataSet.deleteFromSolr(updatedDataSet)
              renderDataSetList(dataSets = List(updatedDataSet))
            case _ =>
              renderDataSetList(responseCode = DataSetResponseCode.STATE_CHANGE_FAILURE)
          }
        case INDEX =>
          state match {
            case DISABLED | UPLOADED =>
              val updatedDataset = changeState(state = DataSetState.QUEUED)
              renderDataSetList(dataSets = List(updatedDataset))
            case _ =>
              renderDataSetList(responseCode = DataSetResponseCode.STATE_CHANGE_FAILURE)
          }
        case REINDEX =>
          state match {
            case ENABLED =>
              val updatedDataSet = changeState(DataSetState.QUEUED)
              renderDataSetList(dataSets = List(updatedDataSet))
            case _ =>
              renderDataSetList(responseCode = DataSetResponseCode.STATE_CHANGE_FAILURE)
          }
        case DELETE =>
          state match {
            case INCOMPLETE | DISABLED | ERROR | UPLOADED =>
              DataSet.remove(dataSet)
              renderDataSetList(dataSets = List(dataSet.copy(state = DataSetState.INCOMPLETE.toString)))
            case _ =>
              renderDataSetList(responseCode = DataSetResponseCode.STATE_CHANGE_FAILURE)
          }
        case _ =>
          throw new RuntimeException
      }
      new RenderXml(response.toString())
    }
    catch {
      case e: Exception => renderException(e)
    }
  }

  private def renderException(exception: Exception): Result = {
    import cake.metaRepo.{DataSetNotFoundException, AccessKeyException}
    log.info("Problem in controller", exception)
    val errorcode = exception match {
      case x if x.isInstanceOf[AccessKeyException] => DataSetResponseCode.ACCESS_KEY_FAILURE
      case x if x.isInstanceOf[DataSetNotFoundException] => DataSetResponseCode.DATA_SET_NOT_FOUND
      case _ => DataSetResponseCode.SYSTEM_ERROR
    }
    renderDataSetListAsXml(responseCode = errorcode, errorMessage = exception.getMessage)
  }

  //  @RequestMapping(value = Array("/dataset/fetch/{dataSetSpec}-sip.zip"), method = Array(RequestMethod.GET))
  def fetchSIP(dataSetSpec: String, accessKey: String, response: Http.Response): Unit = {
//    try {
//      import org.apache.commons.httpclient.HttpStatus
//      checkAccessKey(accessKey)
//      log.info(String.format("requested %s-sip.zip", dataSetSpec))
//      response.setContentType("application/zip")
//      writeSipZip(dataSetSpec, response.getOutputStream, accessKey)
//      response.setStatus(HttpStatus.OK.value)
//      log.info(String.format("returned %s-sip.zip", dataSetSpec))
//    }
//    catch {
//      case e: Exception => {
//        import org.apache.commons.httpclient.HttpStatus
//        response.setStatus(HttpStatus.BAD_REQUEST.value)
//        log.warn("Problem building sip.zip", e)
//      }
//    }
  }

  private def writeSipZip(dataSetSpec: String, outputStream: OutputStream, accessKey: String): Unit = {
    //    import java.util.zip.{ZipEntry, ZipOutputStream}
    //    var dataSet: MetaRepo.DataSet = metaRepo.getDataSet(dataSetSpec)
    //    if (dataSet == null) {
    //      import java.io.IOException
    //      throw new IOException("Data Set not found")
    //    }
    //    var zos: ZipOutputStream = new ZipOutputStream(outputStream)
    //    zos.putNextEntry(new ZipEntry(FileStore.FACTS_FILE_NAME))
    //    var facts: Facts = Facts.fromBytes(dataSet.getDetails.getFacts)
    //    facts.setDownloadedSource(true)
    //    zos.write(Facts.toBytes(facts))
    //    zos.closeEntry
    //    zos.putNextEntry(new ZipEntry(FileStore.SOURCE_FILE_NAME))
    //    var sourceHash: String = writeSourceStream(dataSet, zos, accessKey)
    //    zos.closeEntry
    //    for (mapping <- dataSet.mappings.values) {
    //      import eu.delving.metadata.RecordMapping
    //      var recordMapping: RecordMapping = mapping.getRecordMapping
    //      zos.putNextEntry(new ZipEntry(String.format(FileStore.MAPPING_FILE_PATTERN, recordMapping.getPrefix)))
    //      RecordMapping.write(recordMapping, zos)
    //      zos.closeEntry
    //    }
    //    zos.finish
    //    zos.close
    //    dataSet.setSourceHash(sourceHash, true)
    //    dataSet.save
  }

//  private def writeSourceStream(dataSet: MetaRepo.DataSet, zos: ZipOutputStream, accessKey: String): String = {
//    import eu.delving.metadata.SourceStream
//    import org.bson.types.ObjectId
//    var sourceStream: SourceStream = new SourceStream(zos)
//    sourceStream.startZipStream(dataSet.getNamespaces.toMap)
//    var afterId: ObjectId = null
//    while (true) {
//      import play.modules.legacyServices.eu.delving.core.MetaRepo.DataSet.RecordFetch
////      var fetch: MetaRepo.DataSet#RecordFetch = dataSet.getRecords(dataSet.getDetails.getMetadataFormat.getPrefix, RECORD_STREAM_CHUNK, null, afterId, null, accessKey)
////      if (fetch == null) {
////        break //todo: break is not supported
////      }
//      afterId = fetch.getAfterId
//      for (record <- fetch.getRecords) {
//        sourceStream.addRecord(record.getXmlString)
//      }
//    }
//    sourceStream.endZipStream
//  }
}