package controllers

import models._
import play.{Logger, mvc}
import mvc.results.Result
import mvc.{Before, Controller}
import eu.delving.metadata.{RecordMapping, MetadataModel}
import cake.ComponentRegistry
import models.DataSet
import models.HarvestStep
import extensions.AdditionalActions
import java.util.zip.{ZipEntry, ZipOutputStream, GZIPInputStream}
import play.libs.IO
import com.mongodb.casbah.commons.MongoDBObject
import util.SimpleDataSetParser
import org.apache.commons.io.IOUtils
import java.util.Date
import java.io._
import java.nio.ByteBuffer

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

  val DOT_PLACEHOLDER = "--"

  // HASH__type[_prefix].extension
  private val FileName = """([^_]*)__([^._]*)_?([^.]*).(.*)""".r

  private var connectedUser: Option[User] = None;

  @Before def setUser(): Result = {
    val accessToken: String = params.get("accessKey")
    if (accessToken == null || accessToken.isEmpty) {
      Logger.warn("Service Access Key missing")
      return TextError("No access token provided", 401)
    } else if (!OAuth2TokenEndpoint.isValidToken(accessToken)) {
      Logger.warn("Service Access Key %s invalid!".format(accessToken))
      return TextError(("Access Key %s not accepted".format(accessToken)), 401)
    }
    connectedUser = OAuth2TokenEndpoint.getUserByToken(accessToken)
    Continue
  }

  def getConnectedUser: User = connectedUser.getOrElse({
    Logger.warn("Attemtping to connect with an invalid access token")
    throw new AccessKeyException("No access token provided")
  })

  def getConnectedUserId = getConnectedUser._id

  def listAll(): Result = {
    val dataSets = DataSet.findAllByOwner(getConnectedUserId).toList

    val dataSetsXml = <data-set-list>
      {dataSets.map {
      ds =>
        val user = ds.getUser
        val lockedBy = ds.getLockedBy
        <data-set>
          <spec>{ds.spec}</spec>
          <name>{ds.details.name}</name>
          <ownership>
            <username>{user.reference.username}</username>
            <fullname>{user.fullname}</fullname>
            <email>{user.email}</email>
          </ownership>{if (lockedBy != None) {
          <lockedBy>
            <username>{lockedBy.get.reference.username}</username>
            <fullname>{lockedBy.get.fullname}</fullname>
            <email>{lockedBy.get.email}</email>
          </lockedBy>}}
          <state>{ds.state.toString}</state>
          <recordCount>{ds.details.total_records}</recordCount>
        </data-set>
      }
    }
    </data-set-list>

    Xml(dataSetsXml)
  }

    def unlock(spec: String): Result = {
      val dataSet = DataSet.findBySpec(spec).getOrElse({
        val msg = "Unknown spec %s".format(spec)
        Logger.warn(msg)
        return TextError(msg, 404)
      })
      val updated = dataSet.copy(lockedBy = None)
      DataSet.save(updated)
      Ok
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

    val dataSet: DataSet = DataSet.findBySpec(spec).getOrElse({
      val msg = "DataSet with spec %s not found".format(spec)
      logErrorWithUser(msg)
      return TextError(msg, 404)
    })
    val fileList: String = request.params.get("body")

    Logger.debug("Receiving file upload request, possible files to receive are: \n" + fileList)

    val lines = fileList split('\n')

    def fileRequired(fileName: String): Option[String] = {
      val Array(hash, name) = fileName split("__")
      val maybeHash = dataSet.hashes.get(name.replaceAll("\\.", DOT_PLACEHOLDER))
      maybeHash match {
        case Some(storedHash) if hash != storedHash => Some(fileName)
        case Some(storedHash) if hash == storedHash => None
        case None => Some(fileName)
      }
    }

    val requiredFiles = (lines flatMap fileRequired).mkString("\n")
    Text(requiredFiles)
  }


  def acceptFile: Result = {
    val spec = params.get("spec")
    val fileName = params.get("fileName")
    Logger.info("Accepting file %s for DataSet %s".format(fileName, spec))
    val dataSet = DataSet.findBySpec(spec).getOrElse({
      val msg = "Unknown spec %s".format(spec)
      logErrorWithUser(msg)
      return TextError(msg, 404)})

    val FileName(hash, kind, prefix, extension) = fileName
    if(hash.isEmpty) {
      val msg = "No hash available for file name " + fileName
      logErrorWithUser(msg)
      return TextError(msg)
    }

    val inputStream: InputStream = if (request.contentType == "application/x-gzip") new GZIPInputStream(request.body) else request.body

    val actionResult: Either[String, String] = kind match {
      case "mapping" if extension == "xml" => receiveMapping(dataSet, RecordMapping.read(inputStream, metadataModel), spec, hash)
      case "hints"   if extension == "txt" => receiveHints(dataSet, inputStream)
      case "source"  if extension == "xml.gz" => receiveSource(dataSet, inputStream)
      case "validation"   if extension == "int" => receiveInvalidRecords(dataSet, prefix, inputStream)
      case _ => {
        val msg = "Unknown file type %s".format(kind)
        logErrorWithUser(msg)
        return TextError(msg)
      }
    }

    actionResult match {
      case Right(ok) => {
        DataSet.addHash(dataSet, fileName.split("__")(1).replaceAll("\\.", DOT_PLACEHOLDER), hash)
        Logger.info("Successfully accepted file %s for DataSet %s".format(fileName, spec))
        Ok
      }
      case Left(houston) => {
        logErrorWithUser("Error accepting file %s for DataSet %s: %s".format(fileName, spec, houston))
        TextError(houston)
      }
    }
  }

  private def receiveInvalidRecords(dataSet: DataSet, prefix: String, inputStream: InputStream) = {
    val dis = new DataInputStream(inputStream)
    val howMany = dis.readInt()
    val invalidIndexes: List[Int] = (for(i <- 1 to howMany) yield dis.readInt()).toList
    val updatedDataSet = dataSet.copy(invalidRecords = dataSet.invalidRecords.updated(prefix, invalidIndexes))
    DataSet.save(updatedDataSet)
    Right("All clear")
  }

  private def receiveMapping(dataSet: DataSet, recordMapping: RecordMapping, spec: String, hash: String): Either[String, String] = {
    //if(!DataSet.canUpdate(spec, getConnectedUser)) throw new UnauthorizedException(UNAUTHORIZED_UPDATE)

    HarvestStep.removeFirstHarvestSteps(spec) // todo check if this works
    val updatedDataSet = dataSet.setMapping(mapping = recordMapping)
    DataSet.updateById(updatedDataSet._id, updatedDataSet)
    Right("Good news everybody")
  }

  private def receiveHints(dataSet: DataSet, inputStream: InputStream) = {
    val updatedDataSet = dataSet.copy(hints = IO.readContent(inputStream))
    DataSet.save(updatedDataSet)
    Right("Allright")
  }

  private def receiveSource(dataSet: DataSet, inputStream: InputStream): Either[String, String] = {
//    if(!DataSet.canUpdate(dataSet.spec, getConnectedUser)) throw new UnauthorizedException(UNAUTHORIZED_UPDATE)

    HarvestStep.removeFirstHarvestSteps(dataSet.spec)

    val records = DataSet.getRecords(dataSet)
    records.collection.drop()

    try {

      val parser = new SimpleDataSetParser(inputStream, dataSet)

      var continue = true
      while (continue) {
        val record = parser.nextRecord
        if (record != None) {
          val toInsert = record.get.copy(modified = new Date(), deleted = false)
          records.insert(toInsert)
        } else {
          continue = false
        }
      }
    } catch {
      case t: Throwable => {
        Logger.error(t, "Error occured while parsing records")
        return Left("Error parsing records: " + t.getMessage)
      }
    }

    val recordCount: Int = DataSet.getRecordCount(dataSet.spec)

    val details = dataSet.details.copy(
      total_records = recordCount,
      deleted_records = recordCount - dataSet.details.uploaded_records
    )

    val updatedDataSet = DataSet.findOneByID(dataSet._id).get.copy(details = details, state = DataSetState.UPLOADED) // fetch the latest one, it may have been modified by the parser (namespaces)
    DataSet.save(updatedDataSet)
    Right("Goodbye and thanks for all the fish")
  }

  def fetchSIP(spec: String): Result = {
    val dataSet = {
      val dS = DataSet.findBySpec(spec).getOrElse({
        val msg = "Unknown spec %s".format(spec)
        logErrorWithUser(msg)
        return TextError(msg, 404)
      })
      // lock it right away
      val updatedDataSet = dS.copy(lockedBy = Some(getConnectedUserId))
      DataSet.save(updatedDataSet)
      updatedDataSet
    }

    val name = "%s-sip".format(spec)
    response.setHeader("Content-Disposition", "attachment; filename=\"%s\"".format("attachment", name + ".zip"))

    val zipOut = new ZipOutputStream(new OutputStream {
      // if you see this, look away until I refactor it. I mean there must be a circular buffer of sorts to do this for us
      def write(i: Int) {
        val buffer = ByteBuffer.allocate(8092)
        var count = 0
        if(count == 8092) {
          var a = new Array[Byte](8092)
          buffer.get(a)
          response.writeChunk(a)
          a = new Array[Byte](8092)
          buffer.clear()
        } else {
          buffer.put(i.toByte)
          count = count + 1
        }
      }
    })

    writeEntry("dataset_facts.txt", zipOut) { out =>
      writeContent(dataSet.details.getFactsAsText, out)
    }

    writeEntry("fact-definition-list.xml", zipOut) { out =>
      writeContent(IO.readContentAsString(new File("conf/fact-definition-list.xml")), out)
    }

    writeEntry("hints.txt", zipOut) { out =>
      IOUtils.copy(new ByteArrayInputStream(dataSet.hints), out)
    }

    for(prefix <- dataSet.mappings.keySet) {
      val recordDefinition = prefix + RecordDefinition.RECORD_DEFINITION_SUFFIX
      writeEntry(recordDefinition, zipOut) { out =>
        writeContent(IO.readContentAsString(new File("conf/" + recordDefinition)), out)
      }
    }

    val records = DataSet.getRecords(dataSet)

    if(records.count() > 0) {
      writeEntry("source.xml", zipOut) { out =>
        val pw = new PrintWriter(new OutputStreamWriter(out, "utf-8"))

        val builder = new StringBuilder
        builder.append("<?xml version='1.0' encoding='UTF-8'?>").append("\n")
        builder.append("<delving-sip-source ")
        val attrBuilder = new StringBuilder
        for(ns <- dataSet.namespaces) attrBuilder.append("""xmlns:%s="%s"""".format(ns._1, ns._2)).append(" ")
        builder.append("%s>".format(attrBuilder.toString().trim()))
        write(builder.toString(), pw, out)

        var count = 0
        for(record <- records.find(MongoDBObject()).sort(MongoDBObject("_id" -> 1))) {
          pw.println("<input>")
          pw.println("""<_id>%s</_id>""".format(record.localRecordKey))
          pw.print(record.getXmlString())
          pw.println("</input>")

          if(count % 100 == 0) {
            pw.flush()
            out.flush()
          }
          count += 1
        }
        write("</delving-sip-source>", pw, out)
      }
    }

    for(mapping <- dataSet.mappings) {
      if(mapping._2.recordMapping != None) {
        writeEntry("mapping_%s.xml".format(mapping._1), zipOut) { out =>
          writeContent(mapping._2.recordMapping.get, out)
        }
      }
    }

    zipOut.close()

    Ok
  }

  private def writeEntry(name: String, out: ZipOutputStream)(f: ZipOutputStream => Unit) {
    out.putNextEntry(new ZipEntry(name))
    f(out)
    out.flush()
    out.closeEntry()
  }

  private def writeContent(content: String, out: OutputStream) {
    val printWriter = new PrintWriter(new OutputStreamWriter(out, "utf-8"))
    write(content, printWriter, out)
  }

  private def write(content: String, pw: PrintWriter, out: OutputStream) {
    pw.println(content)
    pw.flush()
    out.flush()
  }

  def logErrorWithUser(msg: String) {
    Logger.error(connectedUser.getOrElse("NotConnected").toString + ": " + msg)
  }

}

