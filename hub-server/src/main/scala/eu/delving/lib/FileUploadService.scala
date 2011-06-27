package eu.delving.lib

import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction._
import net.liftweb.util.Props
import java.lang.RuntimeException
import net.liftweb.http.{FileParamHolder, OnDiskFileParamHolder, JsonResponse}
import java.io.File
import org.apache.commons.io.FileUtils
import at.ait.dme.magicktiler.ptif.PTIFConverter
import at.ait.dme.magicktiler.image.ImageFormat
import at.ait.dme.magicktiler.{TilingException, TilesetInfo, MagickTiler}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileUploadService extends RestHelper {

  serve {
    case request@"service" :: "imageUpload" :: _ Post _ => {
      // TODO move checks to boot
      val imageStoreDir = getPath("image.store.path")
      val imageTmpDir = new File(getPath("image.tmp.path"), "convert")
      val files = for (fp@FileParamHolder(name, _, _, _) <- request.uploadedFiles) yield {
        val onDiskFPH = fp.asInstanceOf[OnDiskFileParamHolder]
        val fileName = if(fp.fileName.indexOf(".") > -1) fp.fileName.substring(0, fp.fileName.indexOf(".")) else fp.fileName
        val targetFile: File = new File(imageStoreDir, fileName + ".tif") // we want tiled TIFs for the moment

        val tiler: MagickTiler = new PTIFConverter()
        tiler.setWorkingDirectory(imageTmpDir)
        tiler.setTileFormat(ImageFormat.JPEG);
        tiler.setJPEGCompressionQuality(75);
        tiler.setBackgroundColor("#ffffffff");
        tiler.setGeneratePreviewHTML(false);

        // working around http://code.google.com/p/magicktiler/issues/detail?id=4
        val tempWithExtension: File = new File(imageTmpDir, onDiskFPH.fileName)

        try {

          FileUtils.copyFile(onDiskFPH.localFile, tempWithExtension)

          System.setProperty("magicktiler.gm.command", getPath("image.graphicsmagic.cmd").getAbsolutePath)
          val info: TilesetInfo = tiler.convert(tempWithExtension, targetFile);

          FileUploadResponse(fp.fileName, onDiskFPH.localFile.length)
        } catch {
          case te: TilingException => {
            te.printStackTrace()
            FileUploadResponse(fp.fileName, onDiskFPH.localFile.length, error = "Error while creating the tileset for the image: '%s'" format (te.getMessage))
          }
          case ex: Throwable => {
            ex.printStackTrace()
            FileUploadResponse(fp.fileName, onDiskFPH.localFile.length, error = "Error while uploading the image: '%s'" format (ex.getMessage))
          }
        } finally {
          tempWithExtension.delete()
        }
      }
      JsonResponse(decompose(files))
    }
  }

  case class FileUploadResponse(name: String, size: Long, url: String = "", thumbnail_url: String = "", delete_url: String = "", delete_type: String = "DELETE", error: String = null)

  private def getPath(key: String): File = {
    val imageStorePath = Props.get(key)
    if (imageStorePath.isEmpty) {
      throw new RuntimeException("You need to configure %s" format (key))
    }
    val imageStore = new File(imageStorePath.openTheBox)
    if (!imageStore.exists()) {
      throw new RuntimeException("Could not find path %s for key %s" format (imageStore.getAbsolutePath, key))
    }
    imageStore
  }


}