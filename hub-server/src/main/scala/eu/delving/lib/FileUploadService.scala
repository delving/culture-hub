package eu.delving.lib

import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction._

import net.liftweb.http.{FileParamHolder, S, JsonResponse}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileUploadService extends RestHelper {

  serve {
    case request@"service" :: "fileUpload" :: _ Post _ => {
      val files = for (fp@FileParamHolder(name, _, _, _) <- request.uploadedFiles) yield FileUploadResponse(fp.fileName, fp.file.length)
      JsonResponse(decompose(files))
    }
  }

  case class FileUploadResponse(name: String, size: Long, url: String = "", thumbnail_url: String = "", delete_url: String = "", delete_type: String = "DELETE")
}