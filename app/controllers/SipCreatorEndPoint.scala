package controllers

import models._
import play.mvc
import mvc.{Before, Controller}
import java.util.Date
import eu.delving.sip.DataSetState
import play.mvc.results.Result
import eu.delving.metadata.{RecordMapping, MetadataModel}
import org.apache.log4j.Logger
import cake.ComponentRegistry
import models.DataSet
import models.HarvestStep
import util.DataSetParser
import extensions.AdditionalActions
import scala.util.matching.Regex
import java.util.zip.{ZipEntry, ZipOutputStream, GZIPInputStream}
import play.libs.IO
import java.io.{File, InputStream}

/**
 * This Controller is responsible for all the interaction with the SIP-Creator.
 * Access control is done using OAuth2
 *
 * TODO check read rights, re-implement access control matrix
 *
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt<bernhardt.manuel@gmail.com>
 * @since 7/7/11 12:04 AM  
 */

object SipCreatorEndPoint extends Controller with AdditionalActions {

  private val UNAUTHORIZED_UPDATE = "You do not have the necessary rights to modify this data set"
  private val metadataModel: MetadataModel = ComponentRegistry.metadataModel
  private val log: Logger = Logger.getLogger(getClass)

  // HASH__type[_prefix].extension
  private val FileName = new Regex("""([^_]*)__([^._]*)_?([^.]*).(.*)""")

  private var connectedUser: Option[User] = None;

  @Before def setUser(): Result = {
    val accessToken: String = params.get("accessKey")
    if (accessToken == null || accessToken.isEmpty) {
      log.warn("Service Access Key missing")
      TextError("No access token provided", 401)
    } else if (!OAuth2TokenEndpoint.isValidToken(accessToken)) {
      log.warn("Service Access Key %s invalid!".format(accessToken))
      TextError(("Access Key %s not accepted".format(accessToken)), 401)
    }
    connectedUser = OAuth2TokenEndpoint.getUserByToken(accessToken)
    Continue
  }

  def getConnectedUser: User = {
    if (connectedUser == None) throw new AccessKeyException("No access token provided")
    connectedUser.get
  }

  def getConnectedUserId = getConnectedUser._id

  def listAll(): Result = {
    val dataSets = DataSet.findAllForUser(getConnectedUser)

    val dataSetsXml = <data-set-list>
      {dataSets.map {
      ds =>
        <dataset>
          <spec>{ds.spec}</spec>
          <name>{ds.details.name}</name>
          <ownership>
            <username>{ds.getUser.reference.username}</username>
            <fullname>{ds.getUser.fullname}</fullname>
            <email>{ds.getUser.email}</email>
          </ownership>
          <state>{ds.state.toString}</state>
          <recordCount>{ds.details.total_records}</recordCount>
        </dataset>
      }
    }
    </data-set-list>

    Xml(dataSetsXml)
  }


  /**
   * Takes a request of filenames and replies with the ones it is missing:
   *
   * 15E64004081B71EE5CA8D55EF735DE44__hints.txt
   * 19EE613335AFBFFAD3F8BA271FBC4E96__mapping_icn.xml
   * 45109F902FCE191BBBFC176287B9B2A4__source.xml.gz
   * 19EE613335AFBFFAD3F8BA271FBC4E96__valid_icn.bit
   */
  def acceptFileList(spec: String): Result = {

    val dataSet: DataSet = DataSet.findBySpec(spec).getOrElse(return Error("DataSet with spec %s not found".format(spec)))
    val fileList: String = request.params.get("body")
    val lines = fileList.split('\n')

    def fileRequired(fileName: String): Option[String] = {
      val split = fileName.split("__")
      val hash = split(0)
      val name = split(1)
      val maybeHash = dataSet.hashes.get(name)
      maybeHash match {
        case Some(storedHash) if hash != storedHash => Some(fileName)
        case Some(storedHash) if hash == storedHash => None
        case None => Some(fileName)
      }
    }

    val requiredFiles: Seq[String] = lines flatMap fileRequired
    val builder: StringBuilder = new StringBuilder
    requiredFiles foreach { f => builder.append(f).append("\n") }
    Text(builder.toString())
  }


  def acceptFile(spec: String, fileName: String): Result = {
    log.info(String.format("Accept file %s for %s", fileName, spec))
    val dataSet = DataSet.findBySpec(spec).getOrElse(return TextError("Unknown spec %s".format(spec)))

    val FileName(hash, kind, prefix, extension) = fileName
    if(hash.isEmpty) return TextError("No hash available for file name " + fileName)

    val inputStream: InputStream = if (request.contentType == "application/x-gzip") new GZIPInputStream(request.body) else request.body

    val actionResult: Either[String, String] = kind match {
      case "mapping" if extension == "xml" => receiveMapping(dataSet, RecordMapping.read(inputStream, metadataModel), spec, hash)
      case "hints"   if extension == "txt" => Left("not yet implemented")
      case "source"  if extension == "xml.gz" => receiveSource(dataSet, inputStream, spec)
      case "valid"   if extension == "bit" => Left("not yet implemented")
      case _ => return TextError("Unknown file type %s".format(kind))
    }

    actionResult match {
      case Right(ok) => {
        DataSet.addHash(dataSet, fileName.split("__")(1), hash)
        Ok
      }
      case Left(houston) => TextError(houston)
    }
  }

  private def receiveMapping(dataSet: DataSet, recordMapping: RecordMapping, spec: String, hash: String): Either[String, String] = {
    //if(!DataSet.canUpdate(spec, getConnectedUser)) throw new UnauthorizedException(UNAUTHORIZED_UPDATE)

    HarvestStep.removeFirstHarvestSteps(spec) // todo check if this works
    val updatedDataSet = dataSet.setMapping(mapping = recordMapping)
    DataSet.updateById(updatedDataSet._id, updatedDataSet)
    Right("Good news everybody")
  }

  private def receiveSource(dataSet: DataSet, inputStream: InputStream, spec: String): Either[String, String] = {
//    if(!DataSet.canUpdate(dataSet.spec, getConnectedUser)) throw new UnauthorizedException(UNAUTHORIZED_UPDATE)

    HarvestStep.removeFirstHarvestSteps(dataSet.spec)

    val records = DataSet.getRecords(dataSet)
    records.collection.drop()

    try {

      val parser = new DataSetParser(inputStream, dataSet.getAllNamespaces, dataSet.details.metadataFormat, dataSet.details.metadataFormat.prefix, null) // TODO

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
      case t: Throwable => return Left("Error parsing records: " + t.getMessage)
    }

    val recordCount: Int = DataSet.getRecordCount(spec)

    val details = dataSet.details.copy(
      total_records = recordCount,
      deleted_records = recordCount - dataSet.details.uploaded_records
    )

    val ds = dataSet.copy(details = details, state = DataSetState.UPLOADED.toString)
    DataSet.save(ds)
    Right("Goodbye and thanks for all the fish")
  }

  def fetchSIP(spec: String): Result = {
    val dataSet = DataSet.findBySpec(spec).getOrElse(return TextError("Unknown spec %s".format(spec)))

    val zipOut = new ZipOutputStream(response.out)

    zipOut.putNextEntry(new ZipEntry("dataset-facts.txt"))
    IO.writeContent(dataSet.details.getFactsAsText, zipOut)
    zipOut.closeEntry()

    zipOut.putNextEntry(new ZipEntry("fact-definition-list.xml"))
    IO.writeContent(IO.readContentAsString(new File("conf/fact-definition-list.xml")), zipOut)
    zipOut.closeEntry()

    // TODO record definitions
    // TODO records
    // TODO mappings

    Ok

  }

}

