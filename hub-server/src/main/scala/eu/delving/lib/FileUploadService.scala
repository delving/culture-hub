package eu.delving.lib

import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction._
import net.liftweb.util.Props
import java.lang.RuntimeException
import net.liftweb.http.{FileParamHolder, OnDiskFileParamHolder, JsonResponse}
import java.io.File
import org.apache.commons.io.FileUtils

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileUploadService extends RestHelper {

  serve {
    case request @ "service" :: "fileUpload" :: _ Post _ => {
      // TODO move checks to boot
      val imageStore = getImageStorePath()
      val files = for (fp @ FileParamHolder(name, _, _, _) <- request.uploadedFiles) yield {
        val onDiskFPH = fp.asInstanceOf[OnDiskFileParamHolder]
        val targetFile: File = new File(imageStore, fp.fileName)
        FileUtils.copyFile(onDiskFPH.localFile, targetFile)
        FileUploadResponse(fp.fileName, onDiskFPH.localFile.length)
      }
      JsonResponse(decompose(files))
    }
  }

  case class FileUploadResponse(name: String, size: Long, url: String = "", thumbnail_url: String = "", delete_url: String = "", delete_type: String = "DELETE")

  private def getImageStorePath: File = {
    val imageStorePath = Props.get("image.store.path")
    if (imageStorePath.isEmpty) {
      throw new RuntimeException("You need to configure image.store.path")
    }
    val imageStore = new File(imageStorePath.openTheBox)
    if (!imageStore.exists()) {
      throw new RuntimeException("Could not find image storage path " + imageStore.getAbsolutePath)
    }
    imageStore
  }


}