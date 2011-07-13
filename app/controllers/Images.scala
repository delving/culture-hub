package controllers

import com.mongodb.casbah.Imports._
import org.apache.log4j.Logger
import java.util.Date
import java.awt.image.BufferedImage
import com.thebuzzmedia.imgscalr.Scalr
import javax.imageio.ImageIO
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import com.mongodb.casbah.gridfs.{GridFSInputFile, GridFSDBFile, GridFS}
import play.mvc.Http.Response
import play.mvc.Controller
import play.mvc.results.{NotFound, RenderBinary, Result}
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.Header
import play.Play

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since 1/2/11 10:09 PM
 */
object Images extends DelvingController {

  val imageCacheService = new ImageCacheService

  def image(`type` : String, id: String, size: String): Result = {
    imageCacheService.retrieveImageFromCache(id, size, response)
  }

  def view(image: String): AnyRef = {
    import views.Image._

    // just for testing
    //val smallballs = getPath("image.store.path") + "/" + image + ".tif"
    val smallballs = Play.applicationPath.getAbsolutePath + "/public/images/smallballs.tif"

    html.view(smallballs)

  }

  def iipsrv(): Result = {
    // TODO this should be a permanent redirect
    Redirect(Play.configuration.getProperty("iipsrv.url") + "?" + request.querystring, true)
  }

}

class ImageCacheService extends HTTPClient {

  val imageCache = MongoConnection().getDB("imageCache")
  val myFS: GridFS = GridFS(imageCache)

  // General Settings
  val thumbnailWidth = 220
  val thumbnailSizeString = "BRIEF_DOC"
  val thumbnailSuffix = "_THUMBNAIL"
  val cacheDuration = 60 * 60 * 24
  private val log: Logger = Logger.getLogger("ImageCacheService")

  // findImageInCache
  def findImageInCache(url: String, thumbnail: Boolean = false): Option[GridFSDBFile] = {
    log info ("attempting to retrieve %s: " format (if (thumbnail) "thumbnail for image" else "image") + " " + url)
    val image: Option[GridFSDBFile] = myFS.findOne(if (thumbnail) url + thumbnailSuffix else url)
    if (image.isDefined) {
      image.get.put("lastViewed", new Date)
      val viewed = image.get("viewed")
      if (viewed != null) image.get.put("viewed", viewed.asInstanceOf[Int] + 1) else image.get.put("viewed", 1)
      image.get.save
    }
    image
  }

  private def sanitizeUrl(url: String): String = {
    val sanitizeUrl: String = url.replaceAll("""\\""", "%5C").replaceAll("\\[", "%5B").replaceAll("\\]", "%5D")
    sanitizeUrl
  }

  def retrieveImageFromCache(url: String, sizeString: String, response: Response): Result = {
    // catch try block to harden the application and always give back a 404 for the application
    try {
      require(url != null)
      require(url != "noImageFound")
      require(!url.isEmpty)
      findOrInsert(sanitizeUrl(url), isThumbnail(Option(sizeString)), response)
    }
    catch {
      case ia: IllegalArgumentException =>
        log.error("problem with processing this url: \"" + url + "\"")
        respondWithNotFound(url)
      case ex: Exception =>
        log.error("unable to find image: \"" + url + "\"\n" + ex.getStackTraceString)
        respondWithNotFound(url)
    }
  }

  def findOrInsert(url: String, thumbnail: Boolean, response: Response): Result = {
    val image: Option[GridFSDBFile] = findImageInCache(url, thumbnail)
    if (!image.isDefined) {
      log info ("image not found attempting to store in cache " + url)
      val item = storeImage(url)
      if (item.available) {
        val storedImage = findImageInCache(url, thumbnail)
        setImageCacheControlHeaders(storedImage.get, response)
        respondWithImageStream(storedImage.get)
      }
      else {
        log info ("unable to store " + url)
        respondWithNotFound(url)
      }
    }
    else {
      setImageCacheControlHeaders(image.get, response)
      respondWithImageStream(image.get)
    }
  }

  // storeImage
  def storeImage(url: String): CachedItem = {
    val image = retrieveImageFromUrl(url)
    if (image.storable) {
      val inputFile = myFS.createFile(image.dataAsStream, image.url)
      inputFile.contentType = image.contentType
      inputFile put ("viewed", 0)
      inputFile put ("lastViewed", new Date)
      inputFile.save

      // also create a thumbnail on the fly
      // for this, fetch the stream again
      val thumbnail: BufferedImage = resizeImage(retrieveImageFromUrl(url).dataAsStream, thumbnailWidth)
      val os: ByteArrayOutputStream = new ByteArrayOutputStream();
      ImageIO.write(thumbnail, "jpg", os);
      val is: InputStream = new ByteArrayInputStream(os.toByteArray);
      val thumbnailFile = myFS.createFile(is, image.url + thumbnailSuffix)
      thumbnailFile.contentType = image.contentType
      thumbnailFile put ("viewed", 0)
      thumbnailFile put ("lastViewed", new Date)
      thumbnailFile.save

      CachedItem(true, inputFile)
    }
    else {
      CachedItem(false, null)
    }
  }


  def retrieveImageFromUrl(url: String) : WebResource = {
    val method = new GetMethod(url)
    getHttpClient executeMethod (method)
    method.getResponseHeaders.foreach(header => log debug (header) )
    val storable = isStorable(method)
    WebResource(url, method.getResponseBodyAsStream, storable._1, storable._2)
  }

  def isStorable(method: GetMethod) = {
    val contentType : Header = method.getResponseHeader("Content-Type")
    val contentLength : Header = method.getResponseHeader("Content-Length")
    val mimeTypes = List("image/png", "image/jpeg", "image/jpg", "image/gif", "image/tiff", "image/pjpeg")
    //todo build a size check in later
    (mimeTypes.contains(contentType.getValue.toLowerCase), contentType.getValue)
  }

  private def respondWithNotFound(url: String): Result = {
    new NotFound(format(
      """<?xml encoding="utf-8"?>
     <error>
       <message>Unable to retrieve your image (%s) through the CacheProxy</message>
     </error> """, url))
  }

  private def respondWithImageStream(image: GridFSDBFile): Result = {
    new RenderBinary(image.inputStream, "cachedImage", image.contentType, true)
  }

  private def setImageCacheControlHeaders(image: GridFSDBFile, response: Response) {
    response.setContentTypeIfNotSet(image.contentType)
    val now = System.currentTimeMillis();
    response.cacheFor("", cacheDuration.toString + "s", now)
    response.headers.get("Cache-Control").values.add("must-revalidate")
    response.setHeader("Expires", (now + cacheDuration * 1000).toString)
  }

  private def isThumbnail(thumbnail: Option[String]): Boolean = {
    thumbnail.getOrElse(false) == thumbnailSizeString
  }

  private def resizeImage(imageStream: InputStream, width: Int): BufferedImage = {
    val bufferedImage: BufferedImage = ImageIO.read(imageStream)
    Scalr.resize(bufferedImage, Scalr.Mode.FIT_TO_WIDTH, width)
  }

}

case class WebResource(url: String, dataAsStream: InputStream, storable: Boolean, contentType: String)

case class CachedItem(available: Boolean, item: GridFSInputFile)

