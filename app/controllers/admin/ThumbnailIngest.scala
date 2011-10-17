package controllers.admin

import controllers.ImageCacheService
import java.io.{FileInputStream, File}
import play.mvc.results.Result
import play.mvc.{Util, Controller}
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject

/**
 * Creates thumbnails from FS images and stores them in the Mongo FileStore
 * 
 * TODO this should become part of a comprehensive interface for batch image ingestion, tiling etc.
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ThumbnailIngest extends Controller {

  val fs = controllers.FileStore.fs

  def ingest(path: String): Result = {
    if(path == null) Error("Null path parameter")
    val dir = new File(path)
    if (!dir.exists() || !dir.isDirectory) {
      Error("Path '%s' is not a valid directory".format(dir))
    } else {
      // yadaaaaa
      val images = dir.listFiles().filter(file => {
        val f = file.getName
        f.endsWith("tif") || f.endsWith("TIF") || f.endsWith("jpg") || f.endsWith("JPG")
      })
      val thumbnails: Seq[(Option[ObjectId], File)] = (images.map { image => (storeThumbnail(image, 180), image) }).toList ::: (images.map { image => (storeThumbnail(image, 500), image) }).toList
      val failures = thumbnails.filter(_._1 == None).map {r => r._2.getAbsolutePath }
      if(failures.size == 0) {
        Text("Successfuly created thumbnails for " + images.length.toString + " images")
      } else {
        Text("Failed to create all thumbnails, " + failures.length + " issues for:\n" + failures.mkString("\n"))
      }
    }
  }

  def remove(path: String): Result = {
    if(path == null) Error("Null path parameter")
    val thumbs = fs.find(MongoDBObject(controllers.FileStore.ORIGIN_PATH_FIELD -> path.r))
    thumbs.foreach {
      t => fs.remove(t.getId.asInstanceOf[ObjectId])
    }
    Text("Removed %s thumbnails".format(thumbs.length))
  }

  @Util private def storeThumbnail(image: File, width: Int): Option[ObjectId] = {
    try {
      val thumbnailStream = ImageCacheService.createThumbnail(new FileInputStream(image), width, true)
      val thumbnail = fs.createFile(thumbnailStream)
      thumbnail.filename = image.getName
      thumbnail.contentType = "image/jpeg"
      val imageName = if (image.getName.indexOf(".") > 0) image.getName.substring(0, image.getName.indexOf(".")) else image.getName
      thumbnail.put(controllers.FileStore.IMAGE_ID_FIELD, imageName)
      thumbnail.put(controllers.FileStore.ORIGIN_PATH_FIELD, image.getAbsolutePath)
      thumbnail.put(controllers.FileStore.THUMBNAIL_WIDTH_FIELD, width.asInstanceOf[AnyRef])
      thumbnail.save
      thumbnail._id
    } catch {
      case t => {
        t.printStackTrace()
        None
      }
    }
  }



}