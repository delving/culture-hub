package controllers.dos

import org.bson.types.ObjectId
import com.mongodb.casbah.gridfs.{GridFS, GridFSDBFile}
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io._
import org.imgscalr.Scalr
import play.api.libs.Files.TemporaryFile
import io.Source

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Thumbnail {

  protected def createThumbnails(image: GridFSDBFile, store: GridFS, globalParams: Map[String, String] = Map.empty[String, String]): Map[Int, ObjectId] = {
    thumbnailSizes.map {
      size => createThumbnailFromStream(image.inputStream, image.filename, image.contentType, size._2, store, globalParams + (FILE_POINTER_FIELD -> image._id.get))
    }
  }

  protected def createThumbnailFromStream(imageStream: InputStream, filename: String, contentType: String, width: Int, store: GridFS, params: Map[String, AnyRef] = Map.empty[String, AnyRef]): (Int, ObjectId) = {
    val resizedStream = createThumbnail(imageStream, contentType, width)
    storeThumbnail(resizedStream, filename, "png", width, store, params)
  }

  protected def storeThumbnail(thumbnailStream: InputStream, filename: String, contentType: String, width: Int, store: GridFS, params: Map[String, AnyRef] = Map.empty[String, AnyRef]): (Int, ObjectId) = {
    val thumbnail = store.createFile(thumbnailStream)
    thumbnail.filename = filename
    thumbnail.contentType = contentType
    thumbnail.put (THUMBNAIL_WIDTH_FIELD, width.asInstanceOf[AnyRef])
    params.foreach { p => thumbnail.put(p._1, p._2) }
    thumbnail.save
    (width, thumbnail._id.get)
  }

  private def createThumbnail(sourceStream: InputStream, contentType: String, thumbnailWidth: Int, boundingBox: Boolean = true): InputStream = {
    val thumbnail: BufferedImage = resizeImage(sourceStream, thumbnailWidth, boundingBox)
    val os: ByteArrayOutputStream = new ByteArrayOutputStream()
    ImageIO.write(thumbnail, "png", os) // we write out the thumbnail as PNG which is a lossless format
    new ByteArrayInputStream(os.toByteArray)
  }

  private def resizeImage(imageStream: InputStream, width: Int, boundingBox: Boolean): BufferedImage = {
    val bufferedImage: BufferedImage = ImageIO.read(imageStream)
    if (boundingBox) {
      Scalr.resize(bufferedImage, width, width)
    } else {
      Scalr.resize(bufferedImage, Scalr.Mode.FIT_TO_WIDTH, width)
    }
  }


}
