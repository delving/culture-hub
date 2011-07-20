package controllers

import java.util.zip.GZIPInputStream
import util.StaxParser
import java.io._
import models._
import eu.delving.metadata.{Hasher, Facts, Path, MetadataNamespace}
import org.apache.log4j.Logger
import play.mvc
import mvc.Controller
import play.libs.IO
import java.util.{Properties, Date}
import eu.delving.sip.{DataSetState}
import models.MetadataRecord

/**
 * This Controller is responsible for all the interaction with the SIP-Creator
 *
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/7/11 12:04 AM  
 */

object Datasets extends Controller {

  import play.mvc.results.Result
  import play.mvc.Http
  import java.io.{OutputStream, InputStream}
  import eu.delving.metadata.{Facts, RecordMapping, MetadataModel}
  import eu.delving.sip.{DataSetResponseCode, AccessKey}
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
                                errorMessage: String = ""): Elem = {
    <data-set responseCode={responseCode.toString}>
      <data-set-list>
        {dataSets.map {
        ds => ds.toXml
      }}
      </data-set-list>{if (responseCode != DataSetResponseCode.THANK_YOU) {
      <errorMessage>
        {errorMessage}
      </errorMessage>
    }}
    </data-set>
  }

  private def renderDataSetListAsXml(responseCode: DataSetResponseCode = DataSetResponseCode.THANK_YOU,
                                     dataSets: List[DataSet] = List.empty[DataSet],
                                     errorMessage: String = ""): Result = {
    new RenderXml(renderDataSetList(responseCode, dataSets, errorMessage).toString)
  }

  def listAll(accessKey: String): Result = {
    try {
      checkAccessToken(accessKey)
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
      checkAccessToken(accessKey)
      indexingControlInternal(dataSetSpec, command)
    }
    catch {
      case e: Exception => renderException(e)
    }
  }

  private def checkAccessToken(accessKey: String) {
    import cake.metaRepo.AccessKeyException
    if (accessKey.isEmpty) {
      log.warn("Service Access Key missing")
      throw new AccessKeyException("Access Key missing")
    }
    else if (!OAuth2TokenEndpoint.isValidToken(accessKey)) {
      log.warn(String.format("Service Access Key %s invalid!", accessKey))
      throw new AccessKeyException(String.format("Access Key %s not accepted", accessKey))
    }
  }

  //  @RequestMapping(value = Array("/dataset/submit/{dataSetSpec}/{fileType}/{fileName}"), method = Array(RequestMethod.POST))


  def acceptFile(dataSetSpec: String, fileType: String, fileName: String, accessKey: String): Result = {
    try {
      import eu.delving.metadata.Hasher
      checkAccessToken(accessKey)
      log.info(String.format("accept type %s for %s: %s", fileType, dataSetSpec, fileName))
      val hash: String = Hasher.extractHashFromFileName(fileName)
      if (hash == null && fileType != "RECORDSTREAM") {
        throw new RuntimeException("No hash available for file name " + fileName)
      }

      val inputStream: InputStream = if (request.contentType == "application/x-gzip") new GZIPInputStream(request.body) else request.body

      val responseCode = fileType match {
        case "FACTS" => receiveFacts(Facts.read(inputStream), dataSetSpec, hash)
        case "HASHES" => return receiveHashes(IO.readUtf8Properties(inputStream), dataSetSpec)
        case "SOURCE" => receiveSource(inputStream, dataSetSpec, hash)
        case "RECORDSTREAM" => receiveRecordStream(inputStream, dataSetSpec)
        case "MAPPING" => receiveMapping(RecordMapping.read(inputStream, metadataModel), dataSetSpec, hash)
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
    HarvestStep.removeFirstHarvestSteps(dataSetSpec) // todo check if this works
    val updatedDataSet = dataSet.setMapping(mapping = recordMapping, hash = hash)
    DataSet.updateById(updatedDataSet._id, updatedDataSet)
    DataSetResponseCode.THANK_YOU
  }

  private def receiveHashes(hashes: Properties, dataSetSpec: String): RenderXml = {
    val dataSet: DataSet = DataSet.getWithSpec(dataSetSpec)
    import scala.collection.JavaConversions.asScalaMap
    val hashesMap: collection.mutable.Map[String, String] = hashes

    import com.mongodb.casbah.Imports.MongoDBObject

    DataSet.getRecords(dataSet).collection.find(MongoDBObject("localRecordKey" -> 1, "globalHash" -> 1)) foreach {
      record =>
        val localRecordKey = record.asInstanceOf[MongoDBObject].getAs[String]("localRecordKey")
        val hash = record.asInstanceOf[MongoDBObject].getAs[String]("globalHash")
        if (localRecordKey != None && hash != None) {
          hashesMap.get(localRecordKey.get) match {
            case r if r == hash => hashesMap.remove(localRecordKey.get) // we have it
            case r if r != hash => // we don't have it
          }
        }
    }

    def renderChangedRecordsList(responseCode: DataSetResponseCode = DataSetResponseCode.THANK_YOU, missingRecords: List[String] = List[String](), errorMessage: String = ""): Elem = {
      <data-set responseCode={responseCode.toString}>
        <changed-records-keys>
          {missingRecords reduceLeft (_ + ", " + _)}
        </changed-records-keys>
      </data-set>
    }

    val string: String = renderChangedRecordsList(missingRecords = hashesMap.keys.toList).toString()
    new RenderXml(string)
  }

  private def receiveRecordStream(inputStream: InputStream, dataSetSpec: String): DataSetResponseCode = {

    val dataSet: DataSet = DataSet.getWithSpec(dataSetSpec)

    try {

      val information = prepareSourceInformation(dataSet)
      val parser = new DataSetParser(inputStream, information._2, information._3, information._1.metadataFormat.prefix, Facts.fromBytes(information._1.facts_bytes))
      val records = DataSet.getRecords(dataSet)

      var continue = true
      while (continue) {
        val record = parser.nextRecord()
        if (record != None) {
          val updatedRecord: MetadataRecord = record.get.copy(modified = new Date(), deleted = false)
          records.upsertByLocalKey(updatedRecord)
        } else {
          continue = false
        }
      }
    } catch {
      case e: Exception => {
        import cake.metaRepo.RecordParseException
        throw new RecordParseException("Unable to parse records", e)
      }
      case t: Throwable => t.printStackTrace()
    }

    DataSetResponseCode.THANK_YOU
  }


  private def receiveSource(inputStream: InputStream, dataSetSpec: String, hash: String): DataSetResponseCode = {

    val dataSet: DataSet = DataSet.getWithSpec(dataSetSpec)
    if (dataSet.hasHash(hash)) {
      return DataSetResponseCode.GOT_IT_ALREADY
    }

    HarvestStep.removeFirstHarvestSteps(dataSet.spec)
    DataSet.save(dataSet.copy(source_hash = ""))

    val records = DataSet.getRecords(dataSet)
    records.collection.drop()

    try {

      val information = prepareSourceInformation(dataSet)
      val parser = new DataSetParser(inputStream, information._2, information._3, information._1.metadataFormat.prefix, Facts.fromBytes(information._1.facts_bytes))

      var continue = true
      while (continue) {
        val record = parser.nextRecord()
        if (record != None) {
          val toInsert = record.get.copy(modified = new Date(), deleted = false)
          records.insert(toInsert)
        } else {
          continue = false
        }
      }
    } catch {
      case e: Exception => {
        import cake.metaRepo.RecordParseException
        throw new RecordParseException("Unable to parse records", e)
      }
      case t: Throwable => t.printStackTrace()
    }

    DataSet.save(dataSet.copy(state = DataSetState.UPLOADED.toString))

    val recordCount: Int = DataSet.getRecordCount(dataSetSpec)

    val details = dataSet.details.copy(
      total_records = recordCount,
      deleted_records = recordCount - dataSet.details.uploaded_records
    )

    val ds = dataSet.copy(source_hash = hash, details = details)
    DataSet.save(ds)
    DataSetResponseCode.THANK_YOU
  }

  private def prepareSourceInformation(dataSet: DataSet) = {
    val details: Details = dataSet.details

    val namespaces = scala.collection.mutable.Map[String, String]()
    for (ns <- MetadataNamespace.values) namespaces.put(ns.getPrefix, ns.getUri)

    val mdFormat: MetadataFormat = dataSet.details.metadataFormat
    namespaces.put(mdFormat.prefix, mdFormat.namespace)

    (details, namespaces, mdFormat)

  }

  private def receiveFacts(facts: Facts, dataSetSpec: String, hash: String): DataSetResponseCode = {
    import models.{MetadataFormat, Details}
    import eu.delving.metadata.MetadataNamespace
    import cake.metaRepo.MetaRepoSystemException
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

    val updatedDataSet: DataSet = {
      import eu.delving.sip.DataSetState
      dataSet match {
        case None => DataSet(spec = dataSetSpec, state = DataSetState.INCOMPLETE.toString, details = details,
          facts_hash = hash)
        case _ => dataSet.get.copy(facts_hash = hash, details = details)
      }
    }
    DataSet.upsertById(updatedDataSet._id, updatedDataSet)

    DataSetResponseCode.THANK_YOU
  }

  private def indexingControlInternal(dataSetSpec: String, commandString: String): Result = {
    try {
      import eu.delving.sip.DataSetCommand._
      import eu.delving.sip.DataSetCommand
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

      val response = command match {
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
              DataSet.delete(dataSet)
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

// TODO rewrite this into something that looks like scala code
class DataSetParser(inputStream: InputStream, namespaces: scala.collection.mutable.Map[String, String], mdFormat: MetadataFormat, metadataPrefix: String, facts: Facts) extends StaxParser {

  import javax.xml.stream.XMLStreamConstants._
  import eu.delving.metadata.Tag
  import scala.collection.mutable.HashMap
  import scala.collection.mutable.MultiMap

  private val log: Logger = Logger.getLogger(getClass)
  private val hasher: Hasher = new Hasher

  private val input = createReader(inputStream)

  private val path: Path = new Path
  private val pathWithinRecord: Path = new Path
  private val recordRoot: Path = new Path(facts.getRecordRootPath)
  private val uniqueElement: Path = new Path(facts.getUniqueElementPath)

  def nextRecord(): Option[MetadataRecord] = {

    var record: Option[MetadataRecord] = None
    val valueMap = new HashMap[String, collection.mutable.Set[String]]() with MultiMap[String, String]

    val xmlBuffer: StringBuilder = new StringBuilder
    val valueBuffer: StringBuilder = new StringBuilder
    var uniqueBuffer: StringBuilder = null
    var uniqueContent: String = null

    var building = true

    while (building) {
      input.getEventType match {
        case START_DOCUMENT =>
        case NAMESPACE => System.out.println("namespace: " + input.getName)
        case START_ELEMENT =>
          path.push(Tag.create(input.getName.getPrefix, input.getName.getLocalPart))
          if (record == None && (path == recordRoot)) {
            record = Some(new MetadataRecord(null, collection.mutable.Map.empty[String, String], new Date(), false, "", "", Map.empty[String, String]))
          }
          if (record != None) {
            pathWithinRecord.push(path.peek)
            if (valueBuffer.length > 0) {
              throw new IOException("Content and subtags not permitted")
            }
            if (path == uniqueElement) uniqueBuffer = new StringBuilder
            val prefix: String = input.getPrefix
            if (prefix != null && !input.getPrefix.isEmpty) {
              namespaces.put(input.getPrefix, input.getNamespaceURI)
            }
            if (path != recordRoot) {
              xmlBuffer.append("<").append(input.getPrefixedName)
              if (input.getAttributeCount > 0) {
                var walk: Int = 0
                while (walk < input.getAttributeCount) {
                  val qName = input.getAttributeName(walk)
                  val attrName = qName.getLocalPart
                  if (qName.getPrefix.isEmpty) {
                    val value = input.getAttributeValue(walk)
                    xmlBuffer.append(' ').append(attrName).append("=\"").append(value).append("\"")
                  }
                  walk += 1
                }
              }
              xmlBuffer.append(">")
            }
          }
        case CDATA | CHARACTERS =>
          if (record != None) {
            val text = input.getText()
            if (!text.trim.isEmpty) {
              var walk: Int = 0
              while (walk < text.length) {
                valueBuffer.append(escape(text, walk))
                walk += 1
              }
              if (uniqueBuffer != null) uniqueBuffer.append(text)
            }
          }
        case END_ELEMENT =>
          if (record != None) {
            if (path == recordRoot) {
              record.get.metadata.put(metadataPrefix, xmlBuffer.toString())
              if (uniqueContent != null) record = Some(record.get.copy(localRecordKey = uniqueContent))
              record = Some(record.get.copy(hash = createHashToPathMap(valueMap), globalHash = hasher.getHashString(xmlBuffer.toString())))
              xmlBuffer.setLength(0)
              building = false
            } else {
              if (valueBuffer.length > 0) {
                if (uniqueBuffer != null) {
                  val unique: String = uniqueBuffer.toString().trim
                  if (!unique.isEmpty) uniqueContent = unique
                  uniqueBuffer = null
                }
                val value: String = valueBuffer.toString()
                xmlBuffer.append(value)
                valueMap.addBinding(pathWithinRecord.toString, value)
              }
              xmlBuffer.append("</").append(input.getPrefixedName).append(">\n")
              valueBuffer.setLength(0)
            }
            pathWithinRecord.pop()
          }
          path.pop()
        case END_DOCUMENT =>
      }
      if (input.hasNext) {
        input.next()
      } else {
        building = false
      }
    }
    record
  }

  def escape(text: String, walk: Int): String = {
    text.charAt(walk) match {
      case '&' => "&amp;"
      case '<' => "&lt;"
      case '>' => "&gt;"
      case '"' => "&quot;"
      case '\'' => "&apos;"
      case c@_ => c.toString
    }
  }

  private def createHashToPathMap(valueMap: MultiMap[String, String]): Map[String, String] = {
    val bits: Iterable[collection.mutable.Set[(String, String)]] = for (path <- valueMap.keys) yield {
      var index: Int = 0
      val innerBits: collection.mutable.Set[(String, String)] = for (value <- valueMap.get(path).get) yield {
        val foo: String = if (index == 0) path else "%s_%d".format(path, index)
        index += 1
        (hasher.getHashString(value), foo)
      }
      innerBits
    }
    bits.flatten.toMap
  }

}