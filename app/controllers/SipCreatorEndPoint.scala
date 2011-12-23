/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import models._
import play.{Logger, mvc}
import mvc.results.{RenderBinary, Result}
import mvc.{Before, Controller}
import eu.delving.metadata.{RecordMapping, MetadataModel}
import cake.ComponentRegistry
import models.DataSet
import models.HarvestStep
import extensions.AdditionalActions
import java.util.zip.{ZipEntry, ZipOutputStream, GZIPInputStream}
import play.libs.IO
import com.mongodb.casbah.commons.MongoDBObject
import java.io._
import util.SimpleDataSetParser
import org.apache.commons.io.{FileCleaningTracker, IOUtils}
import java.util.Date

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

object SipCreatorEndPoint extends Controller with AdditionalActions with Logging {

  private val UNAUTHORIZED_UPDATE = "You do not have the necessary rights to modify this data set"
  private val metadataModel: MetadataModel = ComponentRegistry.metadataModel
  private val fileCleaningTracker = new FileCleaningTracker

  val DOT_PLACEHOLDER = "--"

  // HASH__type[_prefix].extension
  private val FileName = """([^_]*)__([^._]*)_?([^.]*).(.*)""".r

  private var connectedUserObject: Option[User] = None

  private var connectedOrg: Option[Organization] = None

  @Before(priority = 0) def setUser(): Result = {
    val accessToken: String = params.get("accessKey")
    if (accessToken == null || accessToken.isEmpty) {
      TextError("No access token provided", 401)
    } else if (!OAuth2TokenEndpoint.isValidToken(accessToken)) {
      TextError(("Access Key %s not accepted".format(accessToken)), 401)
    } else {
      connectedUserObject = OAuth2TokenEndpoint.getUserByToken(accessToken)
      session += (Authentication.USERNAME, connectedUserObject.get.userName)
      Continue
    }
  }

  @Before(unless = Array("listAll")) def setOrg(): Result = {
    val orgId = params.get("orgId")
    if(orgId == null || orgId.isEmpty) {
      return TextError("No orgId provided", 400)
    }
    connectedOrg = Organization.findByOrgId(orgId)
    if(connectedOrg == None) {
      return TextError("Unknown organization " + orgId)
    }
    Continue
  }

  def getConnectedUser: User = connectedUserObject.getOrElse({
    Logger.warn("Attemtping to connect with an invalid access token")
    throw new AccessKeyException("No access token provided")
  })

  def getConnectedUserId = getConnectedUser._id
  override def connectedUser = getConnectedUser.userName

  def listAll(): Result = {
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

    Xml(dataSetsXml)
  }

    def unlock(orgId: String, spec: String): Result = {
      val dataSet = DataSet.findBySpecAndOrgId(spec, orgId).getOrElse({
        val msg = "Unknown spec %s".format(spec)
        return TextNotFound(msg)
      })

      if(dataSet.lockedBy == None) return Ok

      if(dataSet.lockedBy.get == getConnectedUserId) {
        val updated = dataSet.copy(lockedBy = None)
        DataSet.save(updated)
        Ok
      } else {
        TextError("You cannot unlock a DataSet locked by someone else")
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
  def acceptFileList(orgId: String, spec: String): Result = {

    val dataSet: DataSet = DataSet.findBySpecAndOrgId(spec, orgId).getOrElse({
      val msg = "DataSet with spec %s not found".format(spec)
      return TextNotFound(msg)
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
    val orgId = params.get("orgId")
    val spec = params.get("spec")
    val fileName = params.get("fileName")
    info("Accepting file %s for DataSet %s".format(fileName, spec))
    val dataSet = DataSet.findBySpecAndOrgId(spec, orgId).getOrElse({
      val msg = "Unknown spec %s".format(spec)
      return TextNotFound(msg)})

    val FileName(hash, kind, prefix, extension) = fileName
    if(hash.isEmpty) {
      val msg = "No hash available for file name " + fileName
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
        return TextError(msg)
      }
    }

    actionResult match {
      case Right(ok) => {
        DataSet.addHash(dataSet, fileName.split("__")(1).replaceAll("\\.", DOT_PLACEHOLDER), hash)
        info("Successfully accepted file %s for DataSet %s".format(fileName, spec))
        Ok
      }
      case Left(houston) => {
        TextError("Error accepting file %s for DataSet %s: %s".format(fileName, spec, houston))
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
    if(!DataSet.canEdit(dataSet, connectedUser)) throw new UnauthorizedException(UNAUTHORIZED_UPDATE)

    HarvestStep.removeFirstHarvestSteps(dataSet.spec)

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
        ErrorReporter.reportError(request, params, if(connectedUserObject.isDefined) connectedUserObject.get.userName else "Unknown", t, "Error occured while parsing records for spec %s of org %s", dataSet.spec, dataSet.orgId)
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

  def fetchSIP(orgId: String, spec: String): Result = {
    val dataSet = {
      val dS = DataSet.findBySpecAndOrgId(spec, orgId).getOrElse({
        val msg = "Unknown spec %s".format(spec)
        return TextNotFound(msg)
      })
      // lock it right away
      val updatedDataSet = dS.copy(lockedBy = Some(getConnectedUserId))
      DataSet.save(updatedDataSet)
      updatedDataSet
    }

    val name = "%s-sip".format(spec)
    val tmpFile = File.createTempFile(name, "zip")
    tmpFile.deleteOnExit()
    fileCleaningTracker.track(tmpFile, dataSet)

    val zipOut = new ZipOutputStream(new FileOutputStream(tmpFile))

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

    new RenderBinary(tmpFile, name + ".zip")
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
}

