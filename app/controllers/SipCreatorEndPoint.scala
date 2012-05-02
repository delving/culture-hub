package controllers

import exceptions.{UnauthorizedException, AccessKeyException}
import play.api.mvc._
import core.mapping.MappingService
import java.util.Date
import java.util.zip.{ZipEntry, ZipOutputStream, GZIPInputStream}
import java.io._
import org.apache.commons.io.IOUtils
import com.mongodb.casbah.commons.MongoDBObject
import play.api.libs.iteratee.Enumerator
import extensions.MissingLibs
import play.libs.Akka
import akka.actor.{Props, Actor}
import eu.delving.metadata.RecMapping
import play.api.{Play, Logger}
import play.api.Play.current
import core.{Constants, HubServices}
import scala.{Either, Option}
import util.SimpleDataSetParser
import com.mongodb.casbah.Imports._
import models._

/**
 * This Controller is responsible for all the interaction with the SIP-Creator.
 * Access control is done using OAuth2
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object SipCreatorEndPoint extends ApplicationController {

  private val UNAUTHORIZED_UPDATE = "You do not have the necessary rights to modify this data set"

  val DOT_PLACEHOLDER = "--"

  // HASH__type[_prefix].extension
  private val FileName = """([^_]*)__([^._]*)_?([^.]*).(.*)""".r

  private var connectedUserObject: Option[HubUser] = None

  def AuthenticatedAction[A](accessToken: Option[String])(action: Action[A]): Action[A] = Themed {
    Action(action.parser) {
      implicit request => {
        if (accessToken.isEmpty) {
          Unauthorized("No access token provided")
        } else if (!OAuth2TokenEndpoint.isValidToken(accessToken.get)) {
          Unauthorized("Access Key %s not accepted".format(accessToken.get))
        } else {
          connectedUserObject = OAuth2TokenEndpoint.getUserByToken(accessToken.get)
          val updatedSession = session + (Constants.USERNAME -> connectedUserObject.get.userName)
          val r: PlainResult = action(request).asInstanceOf[PlainResult]
          r.withSession(updatedSession)
        }
      }
    }
  }

  def OrganizationAction[A](orgId: String, accessToken: Option[String])(action: Action[A]): Action[A] = AuthenticatedAction(accessToken) {
    Action(action.parser) {
      implicit request =>
        if (orgId == null || orgId.isEmpty) {
          BadRequest("No orgId provided")
        } else {
          if (!HubServices.organizationService.exists(orgId)) {
            NotFound("Unknown organization " + orgId)
          } else {
            action(request)
          }
        }
    }
  }

  def getConnectedUser: HubUser = connectedUserObject.getOrElse({
    Logger("CultureHub").warn("Attemtping to connect with an invalid access token")
    throw new AccessKeyException("No access token provided")
  })

  def connectedUser = getConnectedUser.userName


  def listAll(accessToken: Option[String]) = AuthenticatedAction(accessToken) {
    Action {
      implicit request =>
        val dataSets = DataSet.findAllForUser(connectedUserObject.get.userName, connectedUserObject.get.organizations, GrantType.MODIFY)

        val dataSetsXml = <data-set-list>
          {dataSets.map {
          ds =>
            val creator = ds.getCreator
            val lockedBy = ds.getLockedBy
            <data-set>
              <spec>{ds.spec}</spec>
              <name>{ds.details.name}</name>
              <orgId>{ds.orgId}</orgId>
              <createdBy>
                <username>{creator.userName}</username>
                <fullname>{creator.fullname}</fullname>
                <email>{creator.email}</email>
              </createdBy>{if (lockedBy != None) {
              <lockedBy>
                <username>{lockedBy.get.userName}</username>
                <fullname>{lockedBy.get.fullname}</fullname>
                <email>{lockedBy.get.email}</email>
              </lockedBy>}}
              <state>{ds.state.name}</state>
              <recordCount>{ds.details.total_records}</recordCount>
            </data-set>
          }
        }
        </data-set-list>


        Ok(dataSetsXml)
    }
  }

  def unlock(orgId: String, spec: String, accessToken: Option[String]): Action[AnyContent] = OrganizationAction(orgId, accessToken) {
    Action {
      implicit request =>
        val dataSet = DataSet.findBySpecAndOrgId(spec, orgId)
        if (dataSet.isEmpty) {
          val msg = "Unknown spec %s".format(spec)
          NotFound(msg)
        } else {
          if (dataSet.get.lockedBy == None) {
            Ok
          } else if (dataSet.get.lockedBy.get == connectedUser) {
            val updated = dataSet.get.copy(lockedBy = None)
            DataSet.save(updated)
            Ok
          } else {
            Error("You cannot unlock a DataSet locked by someone else")
          }
        }
    }
  }

  /**
   * Takes a request of filenames and replies with the ones it is missing:
   *
   * 15E64004081B71EE5CA8D55EF735DE44__hints.txt
   * 19EE613335AFBFFAD3F8BA271FBC4E96__mapping_icn.xml
   * 45109F902FCE191BBBFC176287B9B2A4__source.xml.gz
   * 19EE613335AFBFFAD3F8BA271FBC4E96__valid_icn.bit
   */
  def acceptFileList(orgId: String, spec: String, accessToken: Option[String]): Action[AnyContent] = OrganizationAction(orgId, accessToken) {
    Action {
      implicit request =>

        val dataSet = DataSet.findBySpecAndOrgId(spec, orgId)
        if (dataSet.isEmpty) {
          val msg = "DataSet with spec %s not found".format(spec)
          NotFound(msg)
        } else {
          val fileList: String = request.body.asText.getOrElse("")

          Logger("CultureHub").debug("Receiving file upload request, possible files to receive are: \n" + fileList)

          val lines = fileList.split('\n').map(_.trim).toList

          def fileRequired(fileName: String): Option[String] = {
            val Array(hash, name) = fileName split ("__")
            val maybeHash = dataSet.get.hashes.get(name.replaceAll("\\.", DOT_PLACEHOLDER))
            maybeHash match {
              case Some(storedHash) if hash != storedHash => Some(fileName)
              case Some(storedHash) if hash == storedHash => None
              case None => Some(fileName)
            }
          }
          val requiredFiles = (lines flatMap fileRequired).map(_.trim).mkString("\n")
          Ok(requiredFiles)
        }
    }
  }

  def acceptFile(orgId: String, spec: String, fileName: String, accessToken: Option[String]) = OrganizationAction(orgId, accessToken) {
    Action(parse.temporaryFile) {
      implicit request =>
        val dataSet = DataSet.findBySpecAndOrgId(spec, orgId)
        if (dataSet.isEmpty) {
          val msg = "DataSet with spec %s not found".format(spec)
          NotFound(msg)
        } else {
          val FileName(hash, kind, prefix, extension) = fileName
          if (hash.isEmpty) {
            val msg = "No hash available for file name " + fileName
            Error(msg)
          } else if(request.contentType == None) {
            BadRequest("Request has no content type")
          } else {
            val inputStream = if (request.contentType == Some("application/x-gzip")) new GZIPInputStream(new FileInputStream(request.body.file)) else new FileInputStream(request.body.file)

            val actionResult: Either[String, String] = kind match {
              case "mapping" if extension == "xml" => receiveMapping(dataSet.get, RecMapping.read(inputStream, MappingService.recDefModel), spec, hash)
              case "hints" if extension == "txt" => receiveHints(dataSet.get, inputStream)
              case "source" if extension == "xml.gz" => {
                val receiveActor = Akka.system().actorOf(Props[ReceiveSource])
                receiveActor ! SourceStream(dataSet.get, theme, inputStream)
                DataSet.updateState(dataSet.get, DataSetState.PARSING)
                Right("Received it")
              }
              case "validation" if extension == "int" => receiveInvalidRecords(dataSet.get, prefix, inputStream)
              case x if x.startsWith("stats-") =>
                // politely consume the stream and say goodbye
                Stream.continually(inputStream.read).takeWhile(-1 !=)
                Right("All done")
              case _ => {
                val msg = "Unknown file type %s".format(kind)
                Left(msg)
              }
            }

            actionResult match {
              case Right(ok) => {
                DataSet.addHash(dataSet.get, fileName.split("__")(1).replaceAll("\\.", DOT_PLACEHOLDER), hash)
                info("Successfully accepted file %s for DataSet %s".format(fileName, spec))
                Ok
              }
              case Left(houston) => {
                Error("Error accepting file %s for DataSet %s: %s".format(fileName, spec, houston))
              }
            }
          }
        }
    }
  }


  private def receiveInvalidRecords(dataSet: DataSet, prefix: String, inputStream: InputStream) = {
    val dis = new DataInputStream(inputStream)
    val howMany = dis.readInt()
    val invalidIndexes: List[Int] = (for (i <- 1 to howMany) yield dis.readInt()).toList

    DataSet.updateInvalidRecords(dataSet, prefix, invalidIndexes)

    Right("All clear")
  }

  private def receiveMapping(dataSet: DataSet, recordMapping: RecMapping, spec: String, hash: String): Either[String, String] = {
    if (!DataSet.canEdit(dataSet, connectedUser)) {
      Logger("CultureHub").warn("User %s tried to edit dataSet %s without the necessary rights".format(connectedUser, dataSet.spec))
      throw new UnauthorizedException(UNAUTHORIZED_UPDATE)
    }
    DataSet.updateMapping(dataSet, recordMapping)
    Right("Good news everybody")
  }

  private def receiveHints(dataSet: DataSet, inputStream: InputStream) = {
    val updatedDataSet = dataSet.copy(hints = Stream.continually(inputStream.read).takeWhile(-1 !=).map(_.toByte).toArray)
    DataSet.save(updatedDataSet)
    Right("Allright")
  }

  def fetchSIP(orgId: String, spec: String, accessToken: Option[String]) = OrganizationAction(orgId, accessToken) {
    Action {
      implicit request =>
        val maybeDataSet = DataSet.findBySpecAndOrgId(spec, orgId)
        if (maybeDataSet.isEmpty) {
          NotFound("Unknown spec %s".format(spec))
        } else {
          val dataSet = maybeDataSet.get

          // lock it right away
          val updatedDataSet = dataSet.copy(lockedBy = Some(connectedUser))
          DataSet.save(updatedDataSet)

          val dataContent: Enumerator[Array[Byte]] = Enumerator.fromStream(getSipStream(dataSet))
          Ok.stream(dataContent)
        }
    }
  }


  def getSipStream(dataSet: DataSet) = {
    val in = new PipedInputStream(100000000) // 95 mb buffer... this should use the Iteratee/Enumeratee method instead.
    val zipOut = new ZipOutputStream(new PipedOutputStream(in))

    writeEntry("dataset_facts.txt", zipOut) {
      out =>
        IOUtils.write(dataSet.details.getFactsAsText, out)
    }

    writeEntry("hints.txt", zipOut) {
      out =>
        IOUtils.copy(new ByteArrayInputStream(dataSet.hints), out)
    }

    for (prefix <- dataSet.mappings.keySet) {
      val recordDefinition = prefix + RecordDefinition.RECORD_DEFINITION_SUFFIX
      writeEntry(recordDefinition, zipOut) {
        out =>
          writeContent(MissingLibs.readContentAsString(Play.classloader.getResourceAsStream("definitions/%s/%s-record-definition.xml".format(prefix, prefix))), out)
      }
      val validationSchema = prefix + RecordDefinition.VALIDATION_SCHEMA_SUFFIX
      writeEntry(validationSchema, zipOut) {
        out =>
          writeContent(MissingLibs.readContentAsString(Play.classloader.getResourceAsStream("definitions/%s/%s-validation.xsd".format(prefix, prefix))), out)
      }
    }

    val records = DataSet.getRecords(dataSet)

    if (records.count() > 0) {
      writeEntry("source.xml", zipOut) {
        out =>
          val pw = new PrintWriter(new OutputStreamWriter(out, "utf-8"))
          val builder = new StringBuilder
          builder.append("<?xml version='1.0' encoding='UTF-8'?>").append("\n")
          builder.append("<delving-sip-source ")
          val attrBuilder = new StringBuilder
          for (ns <- dataSet.namespaces) attrBuilder.append("""xmlns:%s="%s"""".format(ns._1, ns._2)).append(" ")
          builder.append("%s>".format(attrBuilder.toString().trim()))
          write(builder.toString(), pw, out)

          var count = 0
          for (record <- records.find(MongoDBObject()).sort(MongoDBObject("_id" -> 1))) {
            pw.println("""<input id="%s">""".format(record.localRecordKey))
            pw.print(record.getRawXmlString)
            pw.println("</input>")

            if (count % 100 == 0) {
              pw.flush()
              out.flush()
            }
            count += 1
          }
          write("</delving-sip-source>", pw, out)
      }
    }


    for (mapping <- dataSet.mappings) {
      if (mapping._2.recordMapping != None) {
        writeEntry("mapping_%s.xml".format(mapping._1), zipOut) {
          out =>
            writeContent(mapping._2.recordMapping.get, out)
        }
      }
    }

    zipOut.close()
    in
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

  def loadSourceData(dataSet: DataSet, source: InputStream): Int = {
    var uploadedRecords = 0

    val records = MetadataCache.get(dataSet.orgId)

    val parser = new SimpleDataSetParser(source, dataSet)

    var continue = true
    while (continue) {
      val maybeNext = parser.nextRecord
      if (maybeNext != None) {
        uploadedRecords += 1
        records.saveOrUpdate(maybeNext.get)
      } else {
        continue = false
      }
    }

    uploadedRecords
  }

}

class ReceiveSource extends Actor {
  
  protected def receive = {
    case SourceStream(dataSet, theme, is) =>
      receiveSource(dataSet, theme, is) match {
        case Left(t) =>
          DataSet.updateState(dataSet, DataSetState.ERROR, Some("Error while parsing DataSet source: " + t.getMessage))
          Logger("CultureHub").error("Error while parsing records for spec %s of org %s".format(dataSet.spec, dataSet.orgId), t)
          ErrorReporter.reportError("DataSet Source Parser", t, "Error occured while parsing records for spec %s of org %s".format(dataSet.spec, dataSet.orgId), theme)
        case _ =>
        // all is good
          Logger("CultureHub").info("Finished parsing source for DataSet %s of organization %s".format(dataSet.spec, dataSet.orgId))
      }
    case _ => // nothing
  }
  
  private def receiveSource(dataSet: DataSet, theme: PortalTheme, inputStream: InputStream): Either[Throwable, String] = {

    var uploadedRecords = 0

    try {
      uploadedRecords = SipCreatorEndPoint.loadSourceData(dataSet, inputStream)
    } catch {
      case t: Throwable => return Left(t)
    }

    val recordCount: Int = DataSet.getRecordCount(dataSet)

    // TODO review the semantics behind total_records, deleted records etc.
    val details = dataSet.details.copy(
      uploaded_records = uploadedRecords,
      total_records = recordCount,
      deleted_records = recordCount - dataSet.details.uploaded_records
    )

    val updatedDataSet = DataSet.findOneByID(dataSet._id).get.copy(details = details, state = DataSetState.UPLOADED) // fetch the DataSet from mongo again, it may have been modified by the parser (namespaces)
    DataSet.save(updatedDataSet)
    Right("Goodbye and thanks for all the fish")
  }


}

case class SourceStream(dataSet: DataSet, theme: PortalTheme, is: InputStream)