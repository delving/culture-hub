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

package controllers.dos

import play.api.mvc._
import play.api.Logger

import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import com.mongodb.gridfs.GridFSDBFile
import com.mongodb.casbah.gridfs.GridFS
import java.util.Date
import play.api.libs.iteratee.Enumerator
import extensions.MissingLibs
import controllers.OrganizationConfigurationAware
import util.OrganizationConfigurationHandler
import java.io.{ FileFilter, File }

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ImageDisplay extends Controller with RespondWithDefaultImage with OrganizationConfigurationAware {

  // ~~ public HTTP API

  /**
   * Display a thumbnail given an ID and a width
   */
  def displayThumbnail(id: String, orgId: String, collectionId: String, width: Option[String], browse: Boolean = false, fileId: Boolean = false, headOnly: Boolean = false) = OrganizationConfigured {
    Action {
      implicit request =>
        renderImage(
          id = id,
          store = fileStore(configuration),
          thumbnail = true,
          orgId = orgId,
          collectionId = collectionId,
          thumbnailWidth = thumbnailWidth(width),
          browse = browse,
          isFileId = fileId,
          headOnly = headOnly
        )
    }
  }

  /**
   * Display an image given an ID
   */
  def displayImage(id: String, fileId: Boolean) = OrganizationConfigured {
    Action {
      implicit request =>
        renderImage(id = id, thumbnail = false, isFileId = fileId, store = fileStore(configuration))
    }
  }

  def displayRawImage(id: String, orgId: String, collectionId: String) = OrganizationConfigured {
    Action {
      implicit request =>
        {
          // check access permissions via api key in configuration
          val wsKey: String = request.getQueryString("wskey").getOrElse("empty")
          val hasAccess: Boolean = configuration.searchService.apiWsKeys.contains(wsKey)
          if (hasAccess) getRawImage(id, orgId, collectionId)
          else {
            Logger.info(s"Not authorised to request raw image for: /raw/$orgId/$collectionId/$id")
            Unauthorized
          }
        }
    }
  }

  // ~~ PRIVATE

  private[dos] def getRawImage(id: String, orgId: String, collectionId: String): Result = {
    val configuration = OrganizationConfigurationHandler.getByOrgId(orgId)

    val tilesWorkingBasePath = new File(configuration.objectService.tilesWorkingBaseDir)
    val rawBasePath = new File(configuration.objectService.tilesWorkingBaseDir + "/raw")

    def checkOrCreate(dir: File) = dir.exists() || !dir.exists() && dir.mkdir()

    if (!checkOrCreate(tilesWorkingBasePath)) {
      Logger.error("Cannot find / create tiles base directory '%s'".format(tilesWorkingBasePath.getAbsolutePath))
      return NotFound
    }

    if (!checkOrCreate(rawBasePath)) {
      Logger.error("Cannot find / create raw base directory '%s'".format(rawBasePath.getAbsolutePath))
      return NotFound
    }

    // check if file exist, with filtering by prefix (only the id is known in the api)
    val rawSourceDir = new File(s"$rawBasePath/$orgId/$collectionId/")
    if (!rawSourceDir.exists()) return NotFound

    val targetFiles = rawSourceDir.listFiles(new FileFilter {
      def accept(p1: File): Boolean = p1.getName.startsWith(id)
    })

    Logger.info(s"requesting files matching $rawSourceDir/$id")
    if (targetFiles.isEmpty) {
      Logger.info(s"File $rawSourceDir/$id not found.")
      NotFound
    } else {
      val sourceFile: File = targetFiles.head
      Logger.info(s"Found file ${sourceFile.getAbsolutePath} and preparing to serve")

      val dataContent: Enumerator[Array[Byte]] = Enumerator.fromFile(sourceFile)
      Mimetype.mimeEncoding(sourceFile) match {
        case Left(t) => Ok.stream(dataContent)
        case Right(t) => Ok.stream(dataContent).withHeaders(CONTENT_TYPE -> t)
      }
    }
  }

  private[dos] def renderImage(id: String,
    orgId: String = "",
    collectionId: String = "",
    thumbnail: Boolean,
    thumbnailWidth: Int = DEFAULT_THUMBNAIL_WIDTH,
    store: GridFS,
    browse: Boolean = false,
    isFileId: Boolean = false,
    headOnly: Boolean = false)(implicit request: Request[AnyContent]): Result = {

    val baseQuery: MongoDBObject = if (ObjectId.isValid(id)) {
      // here we can have different combinations:
      // - we want a thumbnail, and pass in the mongo ObjectId of the file this thumbnail originated from
      // - we want a thumbnail, and pass in the mongo ObjectId of an associated item that can be used to lookup the thumbnail (the thumbnail is "active" for that item id)
      // - we want an image, and pass in the mongo ObjectId of the file
      // - we want an image, and pass in the mongo ObjectId of an associated item that can be used to lookup the image (the image is "active" for that item id)
      val f: String = if (isFileId && thumbnail) {
        FILE_POINTER_FIELD
      } else if (isFileId && !thumbnail) {
        "_id"
      } else if (thumbnail && !isFileId) {
        THUMBNAIL_ITEM_POINTER_FIELD
      } else {
        IMAGE_ITEM_POINTER_FIELD
      }
      MongoDBObject(f -> new ObjectId(id))
    } else {
      // we have a string identifier - from ingested images
      // in order to resolve these we want:
      // - the organization the image belongs to
      // - the collection identifier (spec) the image belongs to
      // - the image identifier (file name minus file extension)

      val idIsUrl = id.startsWith("http://")
      var incomplete = false
      if (!idIsUrl && !browse && (orgId == null || orgId.isEmpty)) {
        Logger("DoS").warn("Attempting to display image '%s' with string identifier without orgId".format(id))
        incomplete = true
      }
      if (!idIsUrl && !browse && (collectionId == null || collectionId.isEmpty)) {
        Logger("DoS").warn("Attempting to display image '%s' with string identifier without collectionId".format(id))
        incomplete = true
      }
      if (browse) {
        MongoDBObject(ORIGIN_PATH_FIELD -> id)
      } else if (idIsUrl || incomplete) {
        MongoDBObject(IMAGE_ID_FIELD -> id)
      } else {
        MongoDBObject(IMAGE_ID_FIELD -> id, ORGANIZATION_IDENTIFIER_FIELD -> orgId, COLLECTION_IDENTIFIER_FIELD -> collectionId)
      }
    }

    val query: MongoDBObject = if (thumbnail) (baseQuery ++ MongoDBObject(THUMBNAIL_WIDTH_FIELD -> thumbnailWidth)) else baseQuery
    val etag = request.headers.get(IF_NONE_MATCH)
    val additionalHeaders = new collection.mutable.HashMap[String, String]
    val image: Option[GridFSDBFile] = store.findOne(query) match {
      case Some(file) => {
        if (isNotExpired(etag, file.underlying)) {
          return NotModified
        } else {
          additionalHeaders += (LAST_MODIFIED -> MissingLibs.getHttpDateFormatter.format(new Date()))
          Some(file.underlying)
        }
      }
      case None if (thumbnail) => {
        // try to find the next fitting size
        store.find(baseQuery).sortWith((a, b) => a.get(THUMBNAIL_WIDTH_FIELD).asInstanceOf[Int] > b.get(THUMBNAIL_WIDTH_FIELD).asInstanceOf[Int]).headOption match {
          case Some(t) =>
            if (isNotExpired(etag, t)) {
              return NotModified
            } else {
              additionalHeaders += (LAST_MODIFIED -> MissingLibs.getHttpDateFormatter.format(new Date()))
              Some(t)
            }
          case None => None
        }
      }
      case None => None

    }
    image match {
      case None if headOnly => NotFound
      case Some(i) if headOnly => Ok
      case None => withDefaultFromRequest(NotFound(request.rawQueryString), thumbnail, Some(thumbnailWidth.toString), false)(request)
      case Some(t) =>
        // cache control
        //        val maxAge: String = Play.configuration.getProperty("http.cacheControl", "3600")
        //        val cacheControl = if (maxAge == "0") "no-cache" else "max-age=" + maxAge
        //        response.setHeader("Cache-Control", cacheControl)
        additionalHeaders += ("ETag" -> t.get("_id").toString)

        val stream = Enumerator.fromStream(t.getInputStream)

        Ok.stream(stream).withHeaders((CONTENT_LENGTH -> t.getLength.toString), (CONTENT_TYPE -> t.getContentType))
    }
  }

  private def isNotExpired(etag: Option[String], oid: GridFSDBFile) = etag != None && etag.get == oid.get("_id").toString

  private[dos] def thumbnailWidth(width: Option[String]): Int = {
    width match {
      case None => DEFAULT_THUMBNAIL_WIDTH
      case Some(w) if thumbnailSizes.contains(w) => thumbnailSizes(w)
      case Some(w) =>
        try {
          Integer.parseInt(w)
        } catch {
          case t: Throwable => DEFAULT_THUMBNAIL_WIDTH
        }
    }
  }
}

import java.io.File
import scala.sys.process._

object Mimetype {
  private val log = Logger(Mimetype.getClass())

  def mimeType(file: File): Either[Int, String] = {
    var retValue: Option[String] = None
    val ret = try {
      Seq("file", "--mime-type", "-b", file.getAbsolutePath) ! ProcessLogger(line => retValue = Some(line))
    } catch {
      case e: Throwable =>
        log.error(e.getMessage)
        -1
    }
    if (ret != 0) {
      Left(ret)
    } else {
      retValue match {
        case Some(mimeType) =>
          if (mimeType startsWith "text") {
            mimeEncoding(file) match {
              case Right(enc) => Right("%smimeType); charset=%s" format (mimeType, enc))
              case Left(ret) => Left(ret)
            }
          } else {
            Right(mimeType)
          }
        case None => Left(-1)
      }
    }
  }

  def mimeEncoding(file: File): Either[Int, String] = {
    var retValue: Option[String] = None
    val ret = try {
      Seq("file", "-e", "cdf", "--mime-encoding", "-b", file.getAbsolutePath) ! ProcessLogger(line => retValue = Some(line))
    } catch {
      case e: Throwable =>
        log.error(e.getMessage)
        -1
    }
    if (ret != 0) {
      Left(ret)
    } else {
      retValue.toRight(-1)
    }
  }

}