package controllers

import exceptions.{ StorageInsertionException, AccessKeyException }
import play.api.mvc._
import java.util.zip.{ ZipEntry, ZipOutputStream, GZIPInputStream }
import java.io._
import org.apache.commons.io.{ FileUtils, IOUtils }
import play.api.libs.iteratee.Enumerator
import play.libs.Akka
import akka.actor.Actor
import play.api.Logger
import core._
import scala.{ Either, Option }
import core.storage.{ BaseXStorage, FileStorage }
import util.SimpleDataSetParser
import java.util.concurrent.TimeUnit
import models._
import play.api.libs.Files.TemporaryFile
import eu.delving.stats.Stats
import scala.collection.JavaConverters._
import java.util.Date
import models.statistics._
import HubMongoContext.hubFileStores
import xml.Node
import play.api.libs.concurrent.Promise
import org.apache.commons.lang.StringEscapeUtils
import scala.util.matching.Regex.Match
import java.util.regex.Matcher
import plugins.DataSetPlugin
import models.statistics.DataSetStatisticsContext
import models.statistics.FieldFrequencies
import models.statistics.FieldValues
import play.api.libs.MimeTypes
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.{ GridFSDBFile, GridFS }
import com.escalatesoft.subcut.inject.BindingModule

/**
 * This Controller is responsible for all the interaction with the SIP-Creator.
 * Access control is done using OAuth2
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class SipCreatorEndPoint(implicit val bindingModule: BindingModule) extends ApplicationController with Logging {

  val organizationServiceLocator = HubModule.inject[DomainServiceLocator[OrganizationService]](name = None)

  val DOT_PLACEHOLDER = "--"

  private def basexStorage(implicit configuration: OrganizationConfiguration) = HubServices.basexStorages.getResource(configuration)

  // HASH__type[_prefix].extension
  private val FileName = """([^_]*)__([^._]*)_?([^.]*).(.*)""".r

  private var connectedUserObject: Option[HubUser] = None

  def AuthenticatedAction[A](accessToken: Option[String])(action: Action[A]): Action[A] = OrganizationConfigured {
    Action(action.parser) {
      implicit request =>
        {
          if (accessToken.isEmpty) {
            Unauthorized("No access token provided")
          } else if (!HubUser.isValidToken(accessToken.get)) {
            Unauthorized("Access Key %s not accepted".format(accessToken.get))
          } else {
            connectedUserObject = HubUser.getUserByToken(accessToken.get)
            action(request)
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
          if (!organizationServiceLocator.byDomain.exists(orgId)) {
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
        val dataSets = DataSet.dao.findAllForUser(
          connectedUserObject.get.userName,
          configuration.orgId,
          DataSetPlugin.ROLE_DATASET_EDITOR
        )

        val dataSetsXml = <data-set-list>{
          dataSets.map {
            ds =>
              val creator = HubUser.dao.findByUsername(ds.getCreator)
              val lockedBy = ds.getLockedBy
              <data-set>
                <spec>{ ds.spec }</spec>
                <name>{ ds.details.name }</name>
                <orgId>{ ds.orgId }</orgId>
                {
                  if (creator.isDefined) {
                    <createdBy>
                      <username>{ creator.get.userName }</username>
                      <fullname>{ creator.get.fullname }</fullname>
                      <email>{ creator.get.email }</email>
                    </createdBy>
                  } else {
                    <createdBy>
                      <username>{ ds.getCreator }</username>
                    </createdBy>
                  }
                }{
                  if (lockedBy != None) {
                    <lockedBy>
                      <username>{ lockedBy.get.userName }</username>
                      <fullname>{ lockedBy.get.fullname }</fullname>
                      <email>{ lockedBy.get.email }</email>
                    </lockedBy>
                  }
                }
                <state>{ ds.state.name }</state>
                <schemaVersions>
                  {
                    ds.getAllMappingSchemas.map { schema =>
                      <schemaVersion>
                        <prefix>{ schema.getPrefix }</prefix>
                        <version>{ schema.getVersion }</version>
                      </schemaVersion>
                    }
                  }
                </schemaVersions>
                <recordCount>{ ds.details.total_records }</recordCount>
              </data-set>
          }
        }</data-set-list>
        Ok(dataSetsXml)
    }
  }

  def unlock(orgId: String, spec: String, accessToken: Option[String]): Action[AnyContent] =
    OrganizationAction(orgId, accessToken) {
      Action {
        implicit request =>
          val dataSet = DataSet.dao.findBySpecAndOrgId(spec, orgId)
          if (dataSet.isEmpty) {
            val msg = "Unknown spec %s".format(spec)
            NotFound(msg)
          } else {
            if (dataSet.get.lockedBy == None) {
              Ok
            } else if (dataSet.get.lockedBy.get == connectedUser) {
              val updated = dataSet.get.copy(lockedBy = None)
              DataSet.dao.save(updated)
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
  def acceptFileList(orgId: String, spec: String, accessToken: Option[String]): Action[AnyContent] =
    OrganizationAction(orgId, accessToken) {
      Action {
        implicit request =>

          val dataSet = DataSet.dao.findBySpecAndOrgId(spec, orgId)
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

  def acceptFile(orgId: String, spec: String, fileName: String, accessToken: Option[String]) =
    OrganizationAction(orgId, accessToken) {
      Action(parse.temporaryFile) {
        implicit request =>
          val dataSet = DataSet.dao.findBySpecAndOrgId(spec, orgId)
          if (dataSet.isEmpty) {
            val msg = "DataSet with spec %s not found".format(spec)
            NotFound(msg)
          } else {
            val FileName(hash, kind, prefix, extension) = fileName
            if (hash.isEmpty) {
              val msg = "No hash available for file name " + fileName
              Error(msg)
            } else if (request.contentType == None) {
              BadRequest("Request has no content type")
            } else if (!DataSet.dao.canEdit(dataSet.get, connectedUser)) {
              log.warn("User %s tried to edit dataSet %s without the necessary rights"
                .format(connectedUser, dataSet.get.spec))
              Forbidden("You are not allowed to modify this DataSet")
            } else {
              val inputStream = if (request.contentType == Some("application/x-gzip"))
                new GZIPInputStream(new FileInputStream(request.body.file))
              else
                new FileInputStream(request.body.file)

              val actionResult: Either[String, String] = kind match {
                case "hints" if extension == "txt" =>
                  receiveHints(dataSet.get, inputStream)

                case "mapping" if extension == "xml" =>
                  receiveMapping(dataSet.get, inputStream, spec, hash)

                case "source" if extension == "xml.gz" => {
                  if (dataSet.get.state == DataSetState.PROCESSING) {
                    Left("%s: Cannot upload source while the set is being processed".format(spec))
                  } else {
                    val receiveActor = Akka.system.actorFor("akka://application/user/plugin-dataSet/dataSetParser")
                    receiveActor ! SourceStream(
                      dataSet.get, connectedUser, inputStream, request.body, configuration
                    )
                    DataSet.dao.updateState(dataSet.get, DataSetState.PARSING)
                    Right("Received it")
                  }
                }

                case "validation" if extension == "int" =>
                  receiveInvalidRecords(dataSet.get, prefix, inputStream)

                case x if x.startsWith("stats-") =>
                  receiveSourceStats(dataSet.get, inputStream, prefix, fileName, request.body.file)

                case "links" =>
                  receiveLinks(dataSet.get, prefix, fileName, request.body.file)

                case "image" =>
                  FileStorage.storeFile(
                    request.body.file,
                    MimeTypes.forFileName(fileName).getOrElse("unknown/unknown"),
                    fileName,
                    dataSet.get.spec,
                    Some("sourceImage"),
                    Map("spec" -> dataSet.get.spec, "orgId" -> dataSet.get.orgId)
                  ).map { f =>
                      Right("Ok")
                    }.getOrElse(
                      Left("Couldn't store file " + fileName)
                    )

                case _ => {
                  val msg = "Unknown file type %s".format(kind)
                  Left(msg)
                }
              }

              actionResult match {
                case Right(ok) => {
                  DataSet.dao.addHash(dataSet.get, fileName.split("__")(1).replaceAll("\\.", DOT_PLACEHOLDER), hash)
                  log.info("Successfully accepted file %s for DataSet %s".format(fileName, spec))
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

    DataSet.dao(dataSet.orgId).updateInvalidRecords(dataSet, prefix, invalidIndexes)

    Right("All clear")
  }

  private def receiveMapping(dataSet: DataSet, inputStream: InputStream, spec: String, hash: String)(implicit configuration: OrganizationConfiguration): Either[String, String] = {
    val mappingString = IOUtils.toString(inputStream, "UTF-8")
    DataSet.dao(dataSet.orgId).updateMapping(dataSet, mappingString)
    Right("Good news everybody")
  }

  private def receiveSourceStats(
    dataSet: DataSet, inputStream: InputStream, schemaPrefix: String, fileName: String, file: File)(implicit configuration: OrganizationConfiguration): Either[String, String] = {
    try {
      val f = hubFileStores.getResource(configuration).createFile(file)

      val stats = Stats.read(inputStream)

      val context = DataSetStatisticsContext(
        dataSet.orgId,
        dataSet.spec,
        schemaPrefix,
        dataSet.details.facts.get("provider").toString,
        dataSet.details.facts.get("dataProvider").toString,
        if (dataSet.details.facts.containsField("providerUri"))
          dataSet.details.facts.get("providerUri").toString
        else
          "",
        if (dataSet.details.facts.containsField("dataProviderUri"))
          dataSet.details.facts.get("dataProviderUri").toString
        else
          "",
        new Date()
      )

      f.put("contentType", "application/x-gzip")
      f.put("orgId", dataSet.orgId)
      f.put("spec", dataSet.spec)
      f.put("schema", schemaPrefix)
      f.put("uploadDate", context.uploadDate)
      f.put("hubFileType", "source-statistics")
      f.put("filename", fileName)
      f.save()

      val dss = DataSetStatistics(
        context = context,
        recordCount = stats.recordStats.recordCount,
        fieldCount = Histogram(stats.recordStats.fieldCount)
      )

      DataSetStatistics.dao.insert(dss).map {
        dssId =>
          {

            stats.fieldValueMap.asScala.foreach {
              fv =>
                val fieldValues = FieldValues(
                  parentId = dssId,
                  context = context,
                  path = fv._1.toString,
                  valueStats = ValueStats(fv._2)
                )
                DataSetStatistics.dao.values.insert(fieldValues)
            }

            stats.recordStats.frequencies.asScala.foreach {
              ff =>
                val frequencies = FieldFrequencies(
                  parentId = dssId,
                  context = context,
                  path = ff._1.toString,
                  histogram = Histogram(ff._2)
                )
                DataSetStatistics.dao.frequencies.insert(frequencies)
            }

            Right("Good")
          }
      }.getOrElse {
        Left("Could not store DataSetStatistics")
      }

    } catch {
      case t: Throwable =>
        t.printStackTrace()
        Left("Error receiving source statistics: " + t.getMessage)
    }
  }

  private def receiveHints(dataSet: DataSet, inputStream: InputStream) = {
    val freshHints = Stream.continually(inputStream.read).takeWhile(-1 != _).map(_.toByte).toArray
    val updatedDataSet = dataSet.copy(hints = freshHints)
    DataSet.dao(dataSet.orgId).save(updatedDataSet)
    Right("Allright")
  }

  private def receiveLinks(dataSet: DataSet, schemaPrefix: String, fileName: String, file: File)(implicit configuration: OrganizationConfiguration) = {
    val store = hubFileStores.getResource(configuration)
    import com.mongodb.casbah.gridfs.Imports._

    // remove previous version
    findLinksFile(dataSet.orgId, dataSet.spec, schemaPrefix, store) foreach { previous =>
      store.remove(previous._id.get)
    }

    val f = store.createFile(file)

    try {
      f.put("contentType", "application/x-gzip")
      f.put("orgId", dataSet.orgId)
      f.put("spec", dataSet.spec)
      f.put("schema", schemaPrefix)
      f.put("uploadDate", new Date())
      f.put("hubFileType", "links")
      f.put("filename", fileName)
      f.save()

      Right("Ok")

    } catch {
      case t: Throwable =>
        log.error("SipCreatorEndPoint: Could not store links", t)
        Left("Error while receiving links: " + t.getMessage)
    }
  }

  private def findLinksFile(orgId: String, spec: String, schemaPrefix: String, store: GridFS): Option[GridFSDBFile] = {
    store.findOne(MongoDBObject("orgId" -> orgId, "spec" -> spec, "schema" -> schemaPrefix, "hubFileType" -> "links"))
  }

  def fetchSIP(orgId: String, spec: String, accessToken: Option[String]) = OrganizationAction(orgId, accessToken) {
    Action {
      implicit request =>
        Async {
          Promise.pure {
            val maybeDataSet = DataSet.dao.findBySpecAndOrgId(spec, orgId)
            if (maybeDataSet.isEmpty) {
              Left(NotFound("Unknown spec %s".format(spec)))
            } else if (maybeDataSet.isDefined && maybeDataSet.get.state == DataSetState.PARSING) {
              Left(Error("DataSet %s is being uploaded at the moment, so you cannot download it at the same time"
                .format(spec)))
            } else {
              val dataSet = maybeDataSet.get

              // lock it right away
              DataSet.dao.lock(dataSet, connectedUser)
              val updatedDataSet = DataSet.dao.findBySpecAndOrgId(spec, orgId).get
              DataSet.dao.save(updatedDataSet)

              val dataContent: Enumerator[Array[Byte]] = Enumerator.fromFile(getSipStream(updatedDataSet))
              Right(dataContent)
            }
          }.map {
            result =>
              if (result.isLeft) {
                result.left.get
              } else {
                Ok.stream(result.right.get)
              }
          }
        }
    }
  }

  def getSipStream(dataSet: DataSet)(implicit configuration: OrganizationConfiguration) = {
    val temp = TemporaryFile(dataSet.spec)
    val fos = new FileOutputStream(temp.file)
    val zipOut = new ZipOutputStream(fos)
    val store = hubFileStores.getResource(configuration)

    writeEntry("dataset_facts.txt", zipOut) {
      out => IOUtils.write(dataSet.details.getFactsAsText, out)
    }

    writeEntry("hints.txt", zipOut) {
      out => IOUtils.copy(new ByteArrayInputStream(dataSet.hints), out)
    }

    store.find(MongoDBObject("orgId" -> dataSet.orgId, "spec" -> dataSet.spec, "hubFileType" -> "links")).toSeq.foreach { links =>
      {
        val prefix: String = links.get("schema").toString
        writeEntry(s"links_$prefix.csv.gz", zipOut) {
          out => IOUtils.copy(links.getInputStream, out)
        }
      }
    }

    val recordCount = basexStorage.count(dataSet)

    if (recordCount > 0) {
      writeEntry("source.xml", zipOut) {
        out => writeDataSetSource(dataSet, out)
      }
    }

    for (mapping <- dataSet.mappings) {
      if (mapping._2.recordMapping != None) {
        writeEntry("mapping_%s.xml".format(mapping._1), zipOut) {
          out => writeContent(mapping._2.recordMapping.get, out)
        }
      }
    }

    zipOut.close()
    fos.close()
    temp.file
  }

  /**
   * Writes the contents of the DataSet, i.e. the data, to the output stream.
   * Rather than going to BaseX each time, we cache the result of the serialization
   * @param dataSet the DataSet for which to write the source data
   * @param out the outputStream
   */
  private def writeDataSetSource(dataSet: DataSet, out: OutputStream)(implicit configuration: OrganizationConfiguration) {
    // is there an already prepared version cached on disk?
    // assume we have a hash
    dataSet.hashes.get("source--xml--gz").map { hash =>
      val prepared = new File(System.getProperty("java.io.tmpdir"), s"/source_${dataSet.orgId}_${dataSet.spec}_$hash.xml")
      if (prepared.exists()) {
        log.info(s"DataSet source ${dataSet.spec} has been cached, writing it directly from ${prepared.getAbsolutePath}")
        IOUtils.copy(new FileInputStream(prepared), out)
      } else {
        log.info(s"DataSet source ${dataSet.spec} has not yet been cached, preparing it")

        try {
          // remove any prior version of a cached stream for this set
          val tmpDir = new File(System.getProperty("java.io.tmpdir"))
          tmpDir.listFiles().find(f => f.getName.startsWith(s"source_${dataSet.orgId}_${dataSet.spec}")) foreach { f =>
            FileUtils.deleteQuietly(f)
          }
        } catch {
          case t: Throwable =>
            log.error("Could not remove prior version of prepared DataSet file", t)
        }

        prepared.createNewFile()
        val fOut = new FileOutputStream(prepared)
        try {
          prepareDataSetSource(dataSet, fOut)
          IOUtils.copy(new FileInputStream(prepared), out)
        } catch {
          case t: Throwable =>
            log.error("Cannot prepare DataSet source file", t)
        } finally {
          fOut.close()
        }
      }
    }.getOrElse {
      log.error("Cannot prepare DataSet source file: no hash found")
    }
  }

  private def prepareDataSetSource(dataSet: DataSet, out: OutputStream)(implicit configuration: OrganizationConfiguration) {

    val now = System.currentTimeMillis()

    val tagContentMatcher = """>([^<]+)<""".r
    val inputTagMatcher = """<input (.*) id="(.*)">""".r

    def buildNamespaces(attrs: Map[String, String]): String = {
      val attrBuilder = new StringBuilder
      attrs.filterNot(_._1.isEmpty).toSeq.sortBy(_._1).foreach(
        ns => attrBuilder.append("""xmlns:%s="%s"""".format(ns._1, ns._2)).append(" ")
      )
      attrBuilder.mkString.trim
    }

    def buildAttributes(attrs: Map[String, String]): String = {
      attrs.map(a => (a._1 -> a._2)).toList.sortBy(_._1).map(
        a => """%s="%s"""".format(a._1, escapeXml(a._2))
      ).mkString(" ")
    }

    def serializeElement(n: Node): String = {
      n match {
        case e if !e.child.filterNot(
          e => e.isInstanceOf[scala.xml.Text] || e.isInstanceOf[scala.xml.PCData]
        ).isEmpty =>
          val content = e.child.filterNot(_.label == "#PCDATA").map(serializeElement(_)).mkString("\n")
          """<%s %s>%s</%s>""".format(
            e.label, buildAttributes(e.attributes.asAttrMap), content + "\n", e.label
          )

        case e if e.child.isEmpty => """<%s/>""".format(e.label)

        case e if !e.attributes.isEmpty => """<%s %s>%s</%s>""".format(
          e.label, buildAttributes(e.attributes.asAttrMap), escapeXml(e.text), e.label
        )

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

    val pw = new PrintWriter(new OutputStreamWriter(out, "utf-8"))
    val builder = new StringBuilder
    builder.append("<?xml version='1.0' encoding='UTF-8'?>").append("\n")
    builder.append("<delving-sip-source ")
    builder.append("%s".format(buildNamespaces(dataSet.getNamespaces)))
    builder.append(">")
    write(builder.toString(), pw, out)

    basexStorage.withSession(dataSet) {
      implicit session =>
        val total = basexStorage.count
        var count = 0
        basexStorage.findAllCurrentDocuments foreach {
          record =>

            // the output coming from BaseX differs from the original source as follows:
            // - the <input> tags contain the namespace declarations
            // - the formatted XML escapes all entities including UTF-8 characters
            // the following lines fix this
            val noNamespaces = inputTagMatcher.replaceSomeIn(record, {
              m => Some("""<input id="%s">""".format(m.group(2)))
            })

            def cleanup: Match => String = {
              s => ">" + escapeXml(StringEscapeUtils.unescapeXml(s.group(1))) + "<"
            }

            val escapeForRegex = Matcher.quoteReplacement(noNamespaces)
            try {
              val cleaned = tagContentMatcher.replaceAllIn(escapeForRegex, cleanup)
              pw.println(cleaned)
            } catch {
              case t: Throwable =>
                log.error(
                  "Error while trying to sanitize following record:\n\n" + escapeForRegex
                )
                throw t
            }

            if (count % 10000 == 0) pw.flush()
            if (count % 10000 == 0) {
              log.info("%s: Prepared %s of %s records for download".format(dataSet
                .spec, count, total))
            }
            count += 1
        }
        pw.print("</delving-sip-source>")
        log.info(s"Done preparing DataSet ${dataSet.spec} for download, it took ${System.currentTimeMillis() - now} ms")
        pw.flush()
    }
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
  }

}

object SipCreatorEndPoint {

  private def basexStorage(implicit configuration: OrganizationConfiguration) = HubServices.basexStorages.getResource(configuration)

  def loadSourceData(dataSet: DataSet, source: InputStream)(implicit configuration: OrganizationConfiguration): Long = {

    // until we have a better concept on how to deal with per-collection versions, do not make use of them here, but drop the data instead
    val mayCollection = basexStorage.openCollection(dataSet)
    val collection = if (mayCollection.isDefined) {
      basexStorage.deleteCollection(mayCollection.get)
      basexStorage.createCollection(dataSet)
    } else {
      basexStorage.createCollection(dataSet)
    }

    val parser = new SimpleDataSetParser(source, dataSet)

    // use the uploaded statistics to know how many records we expect. For that purpose, use the mappings to know what prefixes we have...
    // TODO we should have a more direct route to know what to expect here.
    val totalRecords = dataSet.mappings.keySet.headOption.flatMap {
      schema => DataSetStatistics.dao.getMostRecent(dataSet.orgId, dataSet.spec, schema).map(_.recordCount)
    }
    val modulo = if (totalRecords.isDefined) math.round(totalRecords.get / 100) else 100

    def onRecordInserted(count: Long) {
      if (count % (if (modulo == 0) 100 else modulo) == 0) DataSet.dao.updateRecordCount(dataSet, count)
    }

    basexStorage.store(collection, parser, parser.namespaces, onRecordInserted)
  }
}

class ReceiveSource extends Actor {

  var tempFileRef: TemporaryFile = null

  def receive = {
    case SourceStream(dataSet, userName, inputStream, tempFile, conf) =>
      implicit val configuration = conf
      val now = System.currentTimeMillis()

      // explicitly reference the TemporaryFile so it can't get garbage collected as long as this actor is around
      tempFileRef = tempFile

      try {
        receiveSource(dataSet, userName, inputStream) match {
          case Left(t) =>
            DataSet.dao.invalidateHashes(dataSet)
            val message = if (t.isInstanceOf[StorageInsertionException]) {
              Some("""Error while inserting record:
                      |
                      |%s
                      |
                      |Cause:
                      |
                      |%s
                      | """.stripMargin.format(t.getMessage, t.getCause.getMessage)
              )
            } else {
              Some(t.getMessage)
            }
            DataSet.dao.updateState(dataSet, DataSetState.ERROR, Some(userName), message)
            Logger("CultureHub").error(
              "Error while parsing records for spec %s of org %s".format(
                dataSet.spec, dataSet.orgId
              ),
              t
            )
            ErrorReporter.reportError(
              "DataSet Source Parser", t,
              "Error occured while parsing records for spec %s of org %s".format(
                dataSet.spec, dataSet.orgId
              )
            )

          case Right(inserted) =>
            val duration = Duration(System.currentTimeMillis() - now, TimeUnit.MILLISECONDS)
            Logger("CultureHub").info(
              "Finished parsing source for DataSet %s of organization %s. %s records inserted in %s seconds."
                .format(
                  dataSet.spec, dataSet.orgId, inserted, duration.toSeconds
                )
            )
        }

      } catch {
        case t: Throwable =>
          Logger("CultureHub").error(
            "Exception while processing uploaded source %s for DataSet %s".format(
              tempFile.file.getAbsolutePath, dataSet.spec
            ),
            t
          )
          DataSet.dao.invalidateHashes(dataSet)
          DataSet.dao.updateState(
            dataSet, DataSetState.ERROR, Some(userName),
            Some("Error while parsing uploaded source: " + t.getMessage)
          )

      } finally {
        tempFileRef = null
      }
  }

  private def receiveSource(dataSet: DataSet, userName: String, inputStream: InputStream)(implicit configuration: OrganizationConfiguration): Either[Throwable, Long] = {

    try {
      val uploadedRecords = SipCreatorEndPoint.loadSourceData(dataSet, inputStream)
      DataSet.dao.updateRecordCount(dataSet, uploadedRecords)
      DataSet.dao.updateState(dataSet, DataSetState.UPLOADED, Some(userName))
      Right(uploadedRecords)
    } catch {
      case t: Exception => return Left(t)
    }
  }

}

case class SourceStream(
  dataSet: DataSet,
  userName: String,
  stream: InputStream,
  temporaryFile: TemporaryFile,
  configuration: OrganizationConfiguration)