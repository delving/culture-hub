package controllers

import at.ait.dme.magicktiler.ptif.PTIFConverter
import at.ait.dme.magicktiler.image.ImageFormat
import at.ait.dme.magicktiler.{TilingException, TilesetInfo, MagickTiler}
import org.apache.commons.io.FileUtils
import java.io.File
import play.data.Upload
import scala.collection.JavaConversions._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ImageUpload extends DelvingController {

  import views.ImageUpload._

  def upload(): AnyRef = {
    html.uploadFile()
  }

  def uploadFile(): AnyRef = {

    val imageStoreDir = getPath("image.store.path")
    val imageTmpDir = new File(getPath("image.tmp.path"), "convert")
    if (!imageTmpDir.exists()) {
      imageTmpDir.mkdirs()
    }

    val uploads: java.util.List[Upload] = request.args.get("__UPLOADS").asInstanceOf[java.util.List[Upload]]

    val uploadedFiles = for (upload <- uploads) yield {
      val name: String = upload.getFileName

      val fileName = if (name.indexOf(".") > -1) name.substring(0, name.indexOf(".")) else name
      val targetFile: File = new File(imageStoreDir, fileName + ".tif") // we want tiled TIFs for the moment

      val tiler: MagickTiler = new PTIFConverter()
      tiler.setWorkingDirectory(imageTmpDir)
      tiler.setTileFormat(ImageFormat.JPEG);
      tiler.setJPEGCompressionQuality(75);
      tiler.setBackgroundColor("#ffffff");
      tiler.setGeneratePreviewHTML(false);

      // working around http://code.google.com/p/magicktiler/issues/detail?id=4
      val tempWithExtension: File = new File(imageTmpDir, name)

      try {

        FileUtils.copyFile(upload.asFile(), tempWithExtension)

        System.setProperty("magicktiler.gm.command", getPath("image.graphicsmagic.cmd").getAbsolutePath)
        val info: TilesetInfo = tiler.convert(tempWithExtension, targetFile);

        FileUploadResponse(name, tempWithExtension.length)
      } catch {
        case te: TilingException => {
          te.printStackTrace()
          FileUploadResponse(name, tempWithExtension.length, error = "Error while creating the tileset for the image: '%s'" format (te.getMessage))
        }
        case ex: Throwable => {
          ex.printStackTrace()
          FileUploadResponse(name, tempWithExtension.length, error = "Error while uploading the image: '%s'" format (ex.getMessage))
        }
      } finally {
        tempWithExtension.delete()
      }
    }

    Json(uploadedFiles)
  }

}

case class FileUploadResponse(name: String, size: Long, url: String = "", thumbnail_url: String = "", delete_url: String = "", delete_type: String = "DELETE", error: String = null)