package controllers

import exceptions.{UnauthorizedException, AccessKeyException}
import play.api.mvc._
import models._
import core.mapping.MappingService
import org.apache.commons.io.FileCleaningTracker
import play.api.Logger
import java.util.zip.GZIPInputStream
import eu.delving.metadata.{RecordMapping, MetadataModel}
import java.io.{DataInputStream, InputStream, FileInputStream}
import util.SimpleDataSetParser
import java.util.Date

/**
 * This Controller is responsible for all the interaction with the SIP-Creator.
 * Access control is done using OAuth2
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object SipCreatorEndPoint extends ApplicationController {

  private val UNAUTHORIZED_UPDATE = "You do not have the necessary rights to modify this data set"
  private val metadataModel: MetadataModel = MappingService.metadataModel
  private val fileCleaningTracker = new FileCleaningTracker

  val DOT_PLACEHOLDER = "--"

  // HASH__type[_prefix].extension
  private val FileName = """([^_]*)__([^._]*)_?([^.]*).(.*)""".r

  private var connectedUserObject: Option[User] = None

  private var connectedOrg: Option[Organization] = None


  def AuthenticatedAction[A](accessToken: Option[String])(action: Action[A]): Action[A] = Themed {
    Action(action.parser) {
      implicit request => {
        if (accessToken.isEmpty) {
          Unauthorized("No access token provided")
        } else if (!OAuth2TokenEndpoint.isValidToken(accessToken.get)) {
          Unauthorized("Access Key %s not accepted".format(accessToken))
        } else {
          connectedUserObject = OAuth2TokenEndpoint.getUserByToken(accessToken.get)
          val updatedSession = session + (Authentication.USERNAME -> connectedUserObject.get.userName)
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
          connectedOrg = Organization.findByOrgId(orgId)
          if (connectedOrg == None) {
            NotFound("Unknown organization " + orgId)
          } else {
            action(request)
          }
        }
    }
  }

  def getConnectedUser: User = connectedUserObject.getOrElse({
    Logger("CultureHub").warn("Attemtping to connect with an invalid access token")
    throw new AccessKeyException("No access token provided")
  })

  def getConnectedUserId = getConnectedUser._id
  def connectedUser = getConnectedUser.userName


  def listAll(accessToken: Option[String]) = AuthenticatedAction(accessToken) {
    Action {
      implicit request =>
        val dataSets = DataSet.findAllForUser(connectedUserObject.get.userName, GrantType.MODIFY)

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
                  <state>{ds.state.toString}</state>
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
          if(dataSet.isEmpty) {
          val msg = "Unknown spec %s".format(spec)
          NotFound(msg)
        } else {
            if(dataSet.get.lockedBy == None) {
              Ok
            } else if(dataSet.get.lockedBy.get == getConnectedUserId) {
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
          if(dataSet.isEmpty) {
          val msg = "DataSet with spec %s not found".format(spec)
          NotFound(msg)
        } else {
            val fileList: String = request.body.asText.getOrElse("")

            Logger("CultureHub").debug("Receiving file upload request, possible files to receive are: \n" + fileList)

            val lines = fileList split('\n')

            def fileRequired(fileName: String): Option[String] = {
              val Array(hash, name) = fileName split("__")
              val maybeHash = dataSet.get.hashes.get(name.replaceAll("\\.", DOT_PLACEHOLDER))
              maybeHash match {
                case Some(storedHash) if hash != storedHash => Some(fileName)
                case Some(storedHash) if hash == storedHash => None
                case None => Some(fileName)
              }
          }
          val requiredFiles = (lines flatMap fileRequired).mkString("\n")
          Ok(requiredFiles)
        }
    }
  }

  def acceptFile(orgId: String, spec: String, fileName: String, accessToken: Option[String]) = OrganizationAction(orgId, accessToken) {
    Action(parse.temporaryFile) {
      implicit request =>
        val dataSet = DataSet.findBySpecAndOrgId(spec, orgId)
          if(dataSet.isEmpty) {
            val msg = "DataSet with spec %s not found".format(spec)
            NotFound(msg)
          } else {
            val FileName(hash, kind, prefix, extension) = fileName
            if(hash.isEmpty) {
                val msg = "No hash available for file name " + fileName
                Error(msg)
            }
            val inputStream = if(request.contentType == "application/x-gzip") new GZIPInputStream(new FileInputStream(request.body.file)) else new FileInputStream(request.body.file)

            val actionResult: Either[String, String] = kind match {
              case "mapping" if extension == "xml" => receiveMapping(dataSet.get, RecordMapping.read(inputStream, metadataModel), spec, hash)
              case "hints"   if extension == "txt" => receiveHints(dataSet.get, inputStream)
              case "source"  if extension == "xml.gz" => receiveSource(dataSet.get, inputStream)
              case "validation"  if extension == "int" => receiveInvalidRecords(dataSet.get, prefix, inputStream)
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
  
  
  private def receiveInvalidRecords(dataSet: DataSet, prefix: String, inputStream: InputStream) = {
    val dis = new DataInputStream(inputStream)
    val howMany = dis.readInt()
    val invalidIndexes: List[Int] = (for(i <- 1 to howMany) yield dis.readInt()).toList

    DataSet.updateInvalidRecords(dataSet, prefix, invalidIndexes)

    Right("All clear")
  }

  private def receiveMapping(dataSet: DataSet, recordMapping: RecordMapping, spec: String, hash: String): Either[String, String] = {
    if(!DataSet.canEdit(dataSet, connectedUser)) throw new UnauthorizedException(UNAUTHORIZED_UPDATE)
    DataSet.updateMapping(dataSet, recordMapping)
    Right("Good news everybody")
  }

  private def receiveHints(dataSet: DataSet, inputStream: InputStream) = {
    val updatedDataSet = dataSet.copy(hints = Stream.continually(inputStream.read).takeWhile(-1 !=).map(_.toByte).toArray)
    DataSet.save(updatedDataSet)
    Right("Allright")
  }
  
  private def receiveSource(dataSet: DataSet, inputStream: InputStream)(implicit request: RequestHeader): Either[String, String] = {
    if(!DataSet.canEdit(dataSet, connectedUser)) throw new UnauthorizedException(UNAUTHORIZED_UPDATE)

    val records = DataSet.getRecords(dataSet)
    records.collection.drop()

    var uploadedRecords = 0

    try {

      val parser = new SimpleDataSetParser(inputStream, dataSet)

      var continue = true
      while (continue) {
        val maybeNext = parser.nextRecord
        if (maybeNext != None) {
          uploadedRecords += 1
          val record = maybeNext.get

          // now we need to reconstruct any links that may have existed to this record - if it was re-ingested
          val incomingLinks = Link.findTo(record.getUri(dataSet.orgId, dataSet.spec), Link.LinkType.PARTOF)
          val embeddedLinks = incomingLinks.map(l => EmbeddedLink(TS = new Date(l._id.getTime), userName = l.userName, linkType = l.linkType, link = l._id, value = l.value))

          val toInsert = record.copy(modified = new Date(), deleted = false, links = embeddedLinks)
          records.insert(toInsert)
        } else {
          continue = false
        }
      }
    } catch {
      case t: Throwable => {
        logError(t, "Error while parsing records for spec %s of org %s", dataSet.spec, dataSet.orgId)
        ErrorReporter.reportError(request, t, "Error occured while parsing records for spec %s of org %s".format(dataSet.spec, dataSet.orgId), theme)
        return Left("Error parsing records: " + t.getMessage)
      }
    }

    val recordCount: Int = DataSet.getRecordCount(dataSet)

    // TODO review the semantics behind total_records, deleted records etc.
    val details = dataSet.details.copy(
      uploaded_records = uploadedRecords,
      total_records = recordCount,
      deleted_records = recordCount - dataSet.details.uploaded_records
    )

    val updatedDataSet = DataSet.findOneByID(dataSet._id).get.copy(details = details, state = DataSetState.UPLOADED) // fetch the latest one, it may have been modified by the parser (namespaces)
    DataSet.save(updatedDataSet)
    Right("Goodbye and thanks for all the fish")
  }






}
