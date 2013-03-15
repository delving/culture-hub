package controllers.dos

import org.bson.types.ObjectId
import com.mongodb.casbah.gridfs.{ GridFS, GridFSDBFile }
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io._
import org.imgscalr.Scalr
import play.api.libs.Files.TemporaryFile
import org.apache.commons.io.IOUtils
import org.im4java.core.{ IMOperation, ImageCommand }
import play.api.{ Logger, Play }
import play.api.Play.current
import java.util.UUID
import org.im4java.process.ErrorConsumer

/**
 * TODO refactor the createThumbnails method to allow batch processing on all sizes
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Thumbnail {

  val logger = Logger("CultureHub")

  protected def createThumbnailsFromStream(imageStream: InputStream, fileIdentifier: String, filename: String, contentType: String, store: GridFS, params: Map[String, String] = Map.empty[String, String]): Map[Int, ObjectId] = {
    thumbnailSizes.map { size =>
      logger.info("Creating thumbnail for file " + filename)
      createThumbnailFromStream(imageStream, filename, contentType, size._2, store, params + (FILE_POINTER_FIELD -> fileIdentifier))
    }
  }

  protected def createThumbnails(image: GridFSDBFile, store: GridFS, globalParams: Map[String, String] = Map.empty[String, String]): Map[Int, ObjectId] = {
    thumbnailSizes.map { size =>
      logger.info("Creating thumbnail for file " + image.filename)
      createThumbnailFromStream(image.inputStream, image.filename.getOrElse(""), image.contentType.getOrElse("unknown/unknown"), size._2, store, globalParams + (FILE_POINTER_FIELD -> image._id.get))
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
    thumbnail.put(THUMBNAIL_WIDTH_FIELD, width.asInstanceOf[AnyRef])
    params.foreach { p => thumbnail.put(p._1, p._2) }
    thumbnail.save
    (width, thumbnail._id.get)
  }

  private def createThumbnail(sourceStream: InputStream, contentType: String, thumbnailWidth: Int, boundingBox: Boolean = true): InputStream = {

    if (contentType.contains("pdf")) {

      // this is not the most effective method here since we write the source file out for each size, instead we should run it in batch

      logger.info("Creating thumbnail for PDF with width " + thumbnailWidth)

      val sourceFile = TemporaryFile(UUID.randomUUID().toString, ".pdf")
      val os = new FileOutputStream(sourceFile.file)
      try {
        IOUtils.copy(sourceStream, os)
        os.flush()
      } finally {
        os.close()
      }

      logger.debug("Saved temporary source PDF file at " + sourceFile.file.getAbsolutePath)

      val thumbnailFile = TemporaryFile(UUID.randomUUID().toString, ".png")
      logger.debug("Set temporary thumbnail file path to " + thumbnailFile.file.getAbsolutePath)

      // TODO consolidate all places using GM
      val gmCommand = Play.configuration.getString("dos.graphicsmagic.cmd").getOrElse("")

      val cmd = new ImageCommand(gmCommand, "convert")
      var e: List[String] = List()
      cmd.setErrorConsumer(new ErrorConsumer() {
        def consumeError(is: InputStream) {
          val br = new BufferedReader(new InputStreamReader(is))
          e = Stream.continually(br.readLine()).takeWhile(_ != null).toList
        }
      })
      val op = new IMOperation()
      op.thumbnail(thumbnailWidth)
      op.addImage(sourceFile.file.getAbsolutePath + "[0]") // first page
      op.addImage(thumbnailFile.file.getAbsolutePath)
      logger.info("About to run GM operation: " + cmd.getCommand.toArray.mkString(" ") + " " + op.getCmdArgs.toArray.mkString(" "))
      cmd.run(op)
      if (!e.isEmpty)
        logger.error("Error during creation of thumbnail for PDF file:\n\n " + e.mkString("\n"))

      new FileInputStream(thumbnailFile.file)
    } else {
      val thumbnail: BufferedImage = resizeImage(sourceStream, thumbnailWidth, boundingBox)
      val os: ByteArrayOutputStream = new ByteArrayOutputStream()
      ImageIO.write(thumbnail, "png", os) // we write out the thumbnail as PNG which is a lossless format
      new ByteArrayInputStream(os.toByteArray)
    }

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
