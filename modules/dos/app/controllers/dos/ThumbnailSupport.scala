package controllers.dos

import org.bson.types.ObjectId
import com.mongodb.casbah.gridfs.{ GridFS, GridFSDBFile }
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io._
import org.imgscalr.Scalr
import play.api.libs.Files.TemporaryFile
import org.apache.commons.io.{ FileUtils, IOUtils }
import org.im4java.core.{ IMOperation, ImageCommand }
import play.api.{ Logger, Play }
import play.api.Play.current
import java.util.UUID
import org.im4java.process.ErrorConsumer
import com.mongodb.casbah.commons.MongoDBObject
import models.OrganizationConfiguration

/**
 * TODO refactor the createThumbnails method to allow batch processing on all sizes
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait ThumbnailSupport {

  val logger = Logger("CultureHub")

  protected def createThumbnailsFromStream(imageStream: InputStream, fileIdentifier: String, filename: String, contentType: String, store: GridFS, params: Map[String, String] = Map.empty[String, String])(implicit configuration: OrganizationConfiguration): Map[Int, ObjectId] = {
    thumbnailSizes.map { size =>
      logger.info("Creating thumbnail for file " + filename)
      createThumbnailFromStream(imageStream, filename, contentType, size._2, store, params + (FILE_POINTER_FIELD -> fileIdentifier))
    }
  }

  protected def createThumbnails(image: GridFSDBFile, store: GridFS, globalParams: Map[String, String] = Map.empty[String, String])(implicit configuration: OrganizationConfiguration): Map[Int, ObjectId] = {
    thumbnailSizes.map { size =>
      logger.info("Creating thumbnail for file " + image.filename)
      createThumbnailFromStream(image.inputStream, image.filename.getOrElse(""), image.contentType.getOrElse("unknown/unknown"), size._2, store, globalParams + (FILE_POINTER_FIELD -> image._id.get))
    }
  }

  protected def createThumbnailFromStream(imageStream: InputStream, filename: String, contentType: String, width: Int, store: GridFS, params: Map[String, AnyRef] = Map.empty[String, AnyRef])(implicit configuration: OrganizationConfiguration): (Int, ObjectId) = {
    val resizedStream = createThumbnail(imageStream, contentType, width)
    storeThumbnail(resizedStream, filename, "png", width, store, params)
  }

  protected def storeThumbnail(thumbnailStream: InputStream, filename: String, contentType: String, width: Int, store: GridFS, params: Map[String, AnyRef] = Map.empty[String, AnyRef]): (Int, ObjectId) = {

    // delete previous version if the same file exists in the same context
    if (params.contains(ORGANIZATION_IDENTIFIER_FIELD) && params.contains(COLLECTION_IDENTIFIER_FIELD)) {
      val query = MongoDBObject(
        ORGANIZATION_IDENTIFIER_FIELD -> params(ORGANIZATION_IDENTIFIER_FIELD),
        COLLECTION_IDENTIFIER_FIELD -> params(COLLECTION_IDENTIFIER_FIELD),
        THUMBNAIL_WIDTH_FIELD -> width,
        "filename" -> filename
      )
      store.findOne(query).map { existing =>
        existing._id.map { id =>
          logger.debug(s"Removing existing thumbnail for replacement for file $filename and width $width")
          store.remove(id)
        }
      }
    }

    val thumbnail = store.createFile(thumbnailStream)
    thumbnail.filename = filename
    thumbnail.contentType = contentType
    thumbnail.put(THUMBNAIL_WIDTH_FIELD, width.asInstanceOf[AnyRef])
    params.foreach { p => thumbnail.put(p._1, p._2) }
    thumbnail.save
    (width, thumbnail._id.get)
  }

  private def createThumbnail(sourceStream: InputStream, contentType: String, thumbnailWidth: Int, boundingBox: Boolean = true)(implicit configuration: OrganizationConfiguration): InputStream = {

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

      val cmd = new ImageCommand(configuration.objectService.graphicsMagickCommand, "convert")
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

  /**
   * Creates a thumbnail via GM and stores it
   * TODO merge with PDF method above
   */
  def createAndStoreThumbnail(image: File, width: Int, params: Map[String, AnyRef], store: GridFS, thumbnailTmpDir: File, onSuccess: (Int, File) => Unit, onFailure: (Int, File, String) => Unit)(implicit configuration: OrganizationConfiguration): Option[ObjectId] = {
    // we want JPG thumbnails
    val name = imageName(image.getName) + ".jpg"
    val thumbnailFile = new File(thumbnailTmpDir, name)
    val cmd = new ImageCommand(configuration.objectService.graphicsMagickCommand, "convert")
    var e: List[String] = List()
    cmd.setErrorConsumer(new ErrorConsumer() {
      def consumeError(is: InputStream) {
        val br = new BufferedReader(new InputStreamReader(is))
        e = Stream.continually(br.readLine()).takeWhile(_ != null).toList
      }
    })

    val resizeOperation = new IMOperation()
    resizeOperation.size(width, width)
    resizeOperation.addImage(image.getAbsolutePath)
    resizeOperation.resize(width, width)
    resizeOperation.p_profile("\"*\"")
    resizeOperation.addImage(thumbnailFile.getAbsolutePath)
    try {
      cmd.run(resizeOperation)
      if (thumbnailFile.exists()) {
        val thumb = storeThumbnail(
          thumbnailStream = new BufferedInputStream(new FileInputStream(thumbnailFile)),
          filename = image.getName,
          contentType = "image/jpeg",
          width = width,
          store = store,
          params = params
        )
        onSuccess(width, image)
        Some(thumb._2)
      } else {
        onFailure(width, image, e.mkString("\n"))
        None
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        onFailure(width, image, t.getMessage)
        None
    } finally {
      // cleanup
      FileUtils.deleteQuietly(thumbnailFile)
    }
  }

  /** image name without extension **/
  protected def imageName(name: String) = if (name.indexOf(".") > 0) name.substring(0, name.lastIndexOf(".")) else name

}
