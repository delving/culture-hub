package controllers

import at.ait.dme.magicktiler.ptif.PTIFConverter
import at.ait.dme.magicktiler.image.ImageFormat
import at.ait.dme.magicktiler.{TilingException, TilesetInfo, MagickTiler}
import java.io.File
import scala.collection.JavaConversions._
import user.FileUploadResponse
import org.apache.commons.io.FileUtils
import play.mvc.results.Result

/**
 * Controller taking care of image uploading for tiling
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ImageUpload extends DelvingController {

  val TIF_EXT = ".tif"

  def upload(): Result = Template

  def uploadFile(): AnyRef = {

    val imageStoreDir = getPath("image.store.path", true)
    val imageTileWorkDir = new File(getPath("image.tmp.path", true), "tileWorkDir")
    val imageUploadWorkDir = new File(getPath("image.tmp.path", true), "uploadWorkDir")

    val uploads: java.util.List[play.data.Upload] = request.args.get("__UPLOADS").asInstanceOf[java.util.List[play.data.Upload]]

    val uploadedFiles = for (upload <- uploads) yield {
      val name: String = upload.getFileName

      val fileName = if (name.indexOf(".") > -1) name.substring(0, name.indexOf(".")) else name
      val targetFile: File = new File(imageStoreDir, fileName + TIF_EXT) // we want tiled TIFs for the moment
      targetFile.createNewFile()

      val tiler: MagickTiler = new PTIFConverter()
      tiler.setWorkingDirectory(imageTileWorkDir)
      tiler.setTileFormat(ImageFormat.JPEG);
      tiler.setJPEGCompressionQuality(75);
      tiler.setBackgroundColor("#ffffffff");
      tiler.setGeneratePreviewHTML(false);

      // GM can't work with the temporary file names given by Play
      val tempWithExtension: File = new File(imageUploadWorkDir, name)
      try {
        FileUtils.copyFile(upload.asFile(), tempWithExtension)
        val info: TilesetInfo = tiler.convert(tempWithExtension, targetFile);
        FileUploadResponse(name, tempWithExtension.length, "/image/viewer/" + fileName + TIF_EXT)
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
