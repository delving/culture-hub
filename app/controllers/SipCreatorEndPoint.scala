package controllers

import exceptions.{StorageInsertionException, UnauthorizedException, AccessKeyException}
import play.api.mvc._
import core.mapping.MappingService
import java.util.zip.{ZipEntry, ZipOutputStream, GZIPInputStream}
import java.io._
import org.apache.commons.io.IOUtils
import play.api.libs.iteratee.Enumerator
import extensions.MissingLibs
import play.libs.Akka
import akka.actor.Actor
import eu.delving.metadata.RecMapping
import play.api.{Play, Logger}
import play.api.Play.current
import core.{Constants, HubServices}
import scala.{Either, Option}
import util.SimpleDataSetParser
import core.storage.BaseXCollection
import akka.util.Duration
import java.util.concurrent.TimeUnit
import models._
import play.api.libs.Files.TemporaryFile
import eu.delving.stats.Stats
import scala.collection.JavaConverters._
import java.util.Date
import models.statistics._
import models.mongoContext.hubFileStore
import xml.{Node, NodeSeq, Elem}

/**
 * This Controller is responsible for all the interaction with the SIP-Creator.
 * Access control is done using OAuth2
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object SipCreatorEndPoint extends ApplicationController {

  private val UNAUTHORIZED_UPDATE = "You do not have the necessary rights to modify this data set"

  val DOT_PLACEHOLDER = "--"

  val log: Logger = Logger("CultureHub")

  private lazy val basexStorage = HubServices.basexStorage

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
    log.warn("Attemtping to connect with an invalid access token")
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

          log.debug("Receiving file upload request, possible files to receive are: \n" + fileList)

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
                val receiveActor = Akka.system.actorFor("akka://application/user/dataSetParser")
                receiveActor ! SourceStream(dataSet.get, theme, inputStream, request.body)
                DataSet.updateState(dataSet.get, DataSetState.PARSING)
                Right("Received it")
              }
              case "validation" if extension == "int" => receiveInvalidRecords(dataSet.get, prefix, inputStream)
              case x if x.startsWith("stats-") => receiveSourceStats(dataSet.get, inputStream, request.body.file)
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
      log.warn("User %s tried to edit dataSet %s without the necessary rights".format(connectedUser, dataSet.spec))
      throw new UnauthorizedException(UNAUTHORIZED_UPDATE)
    }
    DataSet.updateMapping(dataSet, recordMapping)
    Right("Good news everybody")
  }

  private def receiveSourceStats(dataSet: DataSet, inputStream: InputStream, file: File): Either[String, String] = {
    try {
      val f = hubFileStore.createFile(file)

      val stats = Stats.read(inputStream)

      val context = DataSetStatisticsContext(dataSet.orgId,
                                             dataSet.spec,
                                             dataSet.details.facts.get("provider").toString,
                                             dataSet.details.facts.get("dataProvider").toString,
                                             if(dataSet.details.facts.containsField("providerUri")) dataSet.details.facts.get("providerUri").toString else "",
                                             if(dataSet.details.facts.containsField("dataProviderUri")) dataSet.details.facts.get("dataProviderUri").toString else "",
                                             new Date())

      f.put("contentType", "application/x-gzip")
      f.put("orgId", dataSet.orgId)
      f.put("spec", dataSet.spec)
      f.put("uploadDate", context.uploadDate)
      f.put("hubFileType", "source-statistics")
      f.save

      val dss = DataSetStatistics(
        context = context,
        recordCount = stats.recordStats.recordCount,
        fieldCount = Histogram(stats.recordStats.fieldCount)
      )

      DataSetStatistics.insert(dss).map {
        dssId => {

          stats.fieldValueMap.asScala.foreach {
            fv =>
              val fieldValues = FieldValues(
                    parentId = dssId,
                    context = context,
                    path = fv._1.toString,
                    valueStats = ValueStats(fv._2)
                  )
              DataSetStatistics.values.insert(fieldValues)
          }

          stats.recordStats.frequencies.asScala.foreach {
            ff =>
              val frequencies = FieldFrequencies(
                    parentId = dssId,
                    context = context,
                    path = ff._1.toString,
                    histogram = Histogram(ff._2)
                  )
              DataSetStatistics.frequencies.insert(frequencies)
          }

          Right("Good")
        }
      }.getOrElse {
        Left("Could not store DataSetStatistics")
      }

    } catch {
      case t =>
        t.printStackTrace()
        Left("Error receiving source statistics: " + t.getMessage)
    }
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

    val collection = BaseXCollection(dataSet.orgId, dataSet.spec)

    val recordCount = basexStorage.count(collection)

    def buildNamespaces(attrs: Map[String, String]): String = {
      val attrBuilder = new StringBuilder
      attrs.foreach(ns => attrBuilder.append(if(ns._1.isEmpty) """xmlns="%s"""".format(ns._2) else """xmlns:%s="%s"""".format(ns._1, ns._2)).append(" "))
      attrBuilder.mkString.trim
    }

    def buildAttributes(attrs: Map[String, String]): String = {
      attrs.map(a => (a._1 -> a._2)).toList.sortBy(_._1).map(a => """%s="%s"""".format(a._1, escapeXml(a._2))).mkString(" ")
    }

    def serializeElement(n: Node): String = {
      n match {
        case e if !e.child.filterNot(e => e.isInstanceOf[scala.xml.Text] || e.isInstanceOf[scala.xml.PCData]).isEmpty =>
          val content = e.child.filterNot(_.label == "#PCDATA").map(serializeElement(_)).mkString("\n")
          """<%s %s>%s</%s>""".format(e.label, buildAttributes(e.attributes.asAttrMap), content + "\n", e.label)
        case e if e.child.isEmpty => """<%s/>""".format(e.label)
        case e if !e.attributes.isEmpty => """<%s %s>%s</%s>""".format(e.label, buildAttributes(e.attributes.asAttrMap), escapeXml(e.text), e.label)
        case e if e.attributes.isEmpty => """<%s>%s</%s>""".format(e.label, escapeXml(e.text), e.label)
        case _ => "" // nope
      }
    }

    // do not use StringEscapeUtils.escapeXml because it also escapes UTF-8 characters, which are however valid and would break source identity
    def escapeXml(s: String): String = {
      s.
        replaceAll("&", "&amp;").
        replaceAll("<", "&lt;").
        replaceAll("&gt;", ">").
        replaceAll("\"", "&quot;").
        replaceAll("'", "&apos;")
    }


    if (recordCount > 0) {
      writeEntry("source.xml", zipOut) {
        out =>
          val pw = new PrintWriter(new OutputStreamWriter(out, "utf-8"))
          val builder = new StringBuilder
          builder.append("<?xml version='1.0' encoding='UTF-8'?>").append("\n")
          builder.append("<delving-sip-source ")
          builder.append("%s".format(buildNamespaces(dataSet.namespaces)))
          builder.append(">")
          write(builder.toString(), pw, out)

          basexStorage.withSession(collection) {
            implicit session => basexStorage.findAllCurrent foreach {
              record =>
                var count = 0
                val input = (record \ "document" \ "input").head
                pw.println("""<input id="%s">""".format(input.attribute("id").get.text))
                input.flatMap(elem => elem match {
                  case e: Elem => e.child
                  case _ => NodeSeq.Empty
                }).filterNot(_.label == "#PCDATA").foreach { node: Node => pw.println(serializeElement(node)) }
                pw.println("</input>")

                if (count % 2000 == 0) {
                  pw.flush()
                  out.flush()
                }
                count += 1
            }
            pw.print("</delving-sip-source>")
            pw.flush()
            out.flush()
          }
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

  def loadSourceData(dataSet: DataSet, source: InputStream): Long = {
    val collection = basexStorage.openCollection(dataSet.orgId, dataSet.spec).getOrElse {
      basexStorage.createCollection(dataSet.orgId, dataSet.spec)
    }
    val parser = new SimpleDataSetParser(source, dataSet)

    def onRecordInserted(count: Long) {
      if(count % 100 == 0) DataSet.updateRecordCount(dataSet, count)
    }

    basexStorage.store(collection, parser, parser.namespaces, onRecordInserted)
  }

}

class ReceiveSource extends Actor {

  var tempFileRef: TemporaryFile = null

  protected def receive = {
    case SourceStream(dataSet, theme, inputStream, tempFile) =>
      val now = System.currentTimeMillis()

      // explicitly reference the TemporaryFile so it can't get garbage collected as long as this actor is around
      tempFileRef = tempFile

      try {
        receiveSource(dataSet, theme, inputStream) match {
          case Left(t) =>
            DataSet.invalidateHashes(dataSet)
            val message = if(t.isInstanceOf[StorageInsertionException]) {
              Some("""Error while inserting record:
                      |
                      |%s
                      |
                      |Cause:
                      |
                      |%s
                      |""".stripMargin.format(t.getMessage, t.getCause.getMessage))
            } else {
              Some(t.getMessage)
            }
            DataSet.updateState(dataSet, DataSetState.ERROR, message)
            Logger("CultureHub").error("Error while parsing records for spec %s of org %s".format(dataSet.spec, dataSet.orgId), t)
            ErrorReporter.reportError("DataSet Source Parser", t, "Error occured while parsing records for spec %s of org %s".format(dataSet.spec, dataSet.orgId), theme)
          case Right(inserted) =>
            val duration = Duration(System.currentTimeMillis() - now, TimeUnit.MILLISECONDS)
            Logger("CultureHub").info("Finished parsing source for DataSet %s of organization %s. %s records inserted in %s seconds.".format(dataSet.spec, dataSet.orgId, inserted, duration.toSeconds))
        }

      } catch {
        case t =>
          Logger("CultureHub").error("Exception while processing uploaded source %s for DataSet %s".format(tempFile.file.getAbsolutePath, dataSet.spec), t)
          DataSet.invalidateHashes(dataSet)
          DataSet.updateState(dataSet, DataSetState.ERROR, Some("Error while parsing uploaded source: " + t.getMessage))

      } finally {
        tempFileRef = null
      }
  }

  private def receiveSource(dataSet: DataSet, theme: PortalTheme, inputStream: InputStream): Either[Throwable, Long] = {

    try {
      val uploadedRecords = SipCreatorEndPoint.loadSourceData(dataSet, inputStream)
      DataSet.updateRecordCount(dataSet, uploadedRecords)
      DataSet.updateState(dataSet, DataSetState.UPLOADED)
      Right(uploadedRecords)
    } catch {
      case t => return Left(t)
    }
  }


}

case class SourceStream(dataSet: DataSet, theme: PortalTheme, stream: InputStream, temporaryFile: TemporaryFile)