package controllers.dos

import play.api.Logger
import play.api.mvc._
import play.api.mvc.Results._

import com.mongodb.casbah.gridfs.Imports._
import com.mongodb.casbah.Implicits._
import java.util.Date
import java.io.InputStream
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.Header
import extensions.HTTPClient


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ImageCache extends Controller with RespondWithDefaultImage {
  val imageCacheService = new ImageCacheService

  def image(id: String, withDefaultFromUrl: Boolean) = Action {
    implicit request =>
      val result = imageCacheService.retrieveImageFromCache(request, id, false)
      if (withDefaultFromUrl) withDefaultFromRequest(result, false, None) else result
  }

  def thumbnail(id: String, width: Option[String], withDefaultFromUrl: Boolean) = Action {
    implicit request =>
      val result = imageCacheService.retrieveImageFromCache(request, id, true, width)
      if (withDefaultFromUrl) withDefaultFromRequest(result, true, width) else result
  }
}

class ImageCacheService extends HTTPClient with Thumbnail {

  private val log: Logger = Logger("ImageCacheService")

  def retrieveImageFromCache(request: Request[AnyContent], url: String, thumbnail: Boolean, thumbnailWidth: Option[String] = None): Result = {
      // catch try block to harden the application and always give back a 404 for the application
      try {
        require(url != null)
        require(url != "noImageFound")
        require(!url.isEmpty)

        val isAvailable = checkOrInsert(sanitizeUrl(url))
        isAvailable match {
          case false => NotFound(url)
          case true => ImageDisplay.renderImage(id = url, thumbnail = thumbnail, thumbnailWidth = ImageDisplay.thumbnailWidth(thumbnailWidth), store = imageCacheStore)(request)
        }

      } catch {
        case ia: IllegalArgumentException =>
          log.error("problem with processing this url: \"" + url + "\"")
          NotFound(url)
        case ex: Exception =>
          log.error("unable to find image: \"" + url + "\"\n" + ex.getStackTraceString)
          NotFound(url)
      }
  }

  def checkOrInsert(url: String): Boolean = {
    if(isImageCached(url)) true else {
      log info ("image not found, attempting to store it in the cache based on URL: " + url)
      val stored = storeImage(url)
      if(stored) {
        log info ("successfully cached image for URL: " + url)
        true
      } else {
        log info ("unable to store " + url)
        false
      }
    }
  }

  private def isImageCached(url: String): Boolean = {
    log info ("attempting to retrieve image for URL " + url)
    imageCacheStore.findOne(url) != None
  }

  private def sanitizeUrl(url: String): String = {
    val sanitizeUrl: String = url.replaceAll("""\\""", "%5C").replaceAll("\\[", "%5B").replaceAll("\\]", "%5D")
    sanitizeUrl
  }

  private def storeImage(url: String): Boolean = {
    val image = retrieveImageFromUrl(url)
    if (image.storable) {
      val inputFile = imageCacheStore.createFile(image.dataAsStream, image.url)
      inputFile.contentType = image.contentType
      inputFile put (IMAGE_ID_FIELD, image.url)
      inputFile put("viewed", 0)
      inputFile put("lastViewed", new Date)
      inputFile.save

      val cachedImage = imageCacheStore.findOne(image.url).getOrElse(return false)
      createThumbnails(cachedImage, imageCacheStore, Map(IMAGE_ID_FIELD -> image.url))
      true
    } else {
      false
    }
  }

  private def retrieveImageFromUrl(url: String): WebResource = {
    val method = new GetMethod(url)
    getHttpClient executeMethod (method)
    method.getResponseHeaders.foreach(header => log.debug(header.toString))
    val storable = isStorable(method)
    WebResource(url, method.getResponseBodyAsStream, storable._1, storable._2)
  }

  private def isStorable(method: GetMethod) = {
    val contentType: Header = method.getResponseHeader("Content-Type")
    val contentLength: Header = method.getResponseHeader("Content-Length")
    val mimeTypes = List("image/png", "image/jpeg", "image/jpg", "image/gif", "image/tiff", "image/pjpeg")
    //todo build a size check in later
    (mimeTypes.contains(contentType.getValue.toLowerCase), contentType.getValue)
  }

}

case class WebResource(url: String, dataAsStream: InputStream, storable: Boolean, contentType: String)
