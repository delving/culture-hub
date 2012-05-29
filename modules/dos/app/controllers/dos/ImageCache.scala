package controllers.dos

import play.api.Logger
import play.api.mvc._
import play.api.mvc.Results._

import com.mongodb.casbah.Imports._
import java.util.Date
import java.io.InputStream
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.Header
import extensions.HTTPClient
import com.mongodb.casbah.commons.MongoDBObject
import java.net.URLDecoder


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ImageCache extends Controller with RespondWithDefaultImage {
  val imageCacheService = new ImageCacheService

  def image(id: String, withDefaultFromUrl: Boolean) = Action {
    implicit request =>
      val result = imageCacheService.retrieveImageFromCache(request, URLDecoder.decode(id, "utf-8"), false)
      if (withDefaultFromUrl) withDefaultFromRequest(result, false, None) else result
  }

  def thumbnail(id: String, width: Option[String], withDefaultFromUrl: Boolean) = Action {
    implicit request =>
      val result = imageCacheService.retrieveImageFromCache(request, URLDecoder.decode(id, "utf-8"), true, width)
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

        val sanitizedUrl = sanitizeUrl(url)

        val isAvailable = checkOrInsert(sanitizedUrl)
        if(isAvailable) {
          imageCacheStoreConnection("fs.files").update(MongoDBObject("filename" -> sanitizedUrl), ($inc ("viewed" -> 1)) ++ $set ("lastViewed" -> new Date))
          ImageDisplay.renderImage(id = sanitizedUrl, thumbnail = thumbnail, thumbnailWidth = ImageDisplay.thumbnailWidth(thumbnailWidth), store = imageCacheStore)(request)
        } else {
          NotFound(sanitizedUrl)
        }

      } catch {
        case ia: IllegalArgumentException =>
          log.error("Problem with processing this url: \"" + sanitizeUrl(url) + "\"", ia)
          NotFound(sanitizeUrl(url))
        case ex: Exception =>
          log.error("Unable to find image: \"" + sanitizeUrl(url) + "\"\n", ex)
          NotFound(sanitizeUrl(url))
      }
  }

  def checkOrInsert(url: String): Boolean = {
    if(isImageCached(url)) true else {
      log.info("Image not found, attempting to store it in the cache based on URL: '" + url + "'")
      val stored = storeImage(url)
      if(stored) {
        log.debug("Successfully cached image for URL: '" + url + "'")
        true
      } else {
        log.info("Unable to store '" + url + "'")
        false
      }
    }
  }

  private def isImageCached(url: String): Boolean = {
    log.debug("Attempting to retrieve image for URL " + url)
    imageCacheStore.findOne(MongoDBObject("filename" -> url)) != None
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
    WebResource(method)
  }

}

case class WebResource(url: String, dataAsStream: InputStream, storable: Boolean, contentType: String)

case object WebResource {

  def apply(method: GetMethod): WebResource = {
    val contentType = method.getResponseHeader("Content-Type").getValue.toLowerCase.split(",").headOption.getOrElse("")
    // TODO sanity check on length
    val contentLength: Header = method.getResponseHeader("Content-Length")
    val mimeTypes = List("image/png", "image/jpeg", "image/jpg", "image/gif", "image/tiff", "image/pjpeg")
    WebResource(method.getURI.toString, method.getResponseBodyAsStream, mimeTypes.contains(contentType), contentType)
  }
}
