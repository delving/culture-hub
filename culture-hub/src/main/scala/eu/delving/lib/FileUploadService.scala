package eu.delving.lib

import net.liftweb.http.rest.RestHelper
import net.liftweb.http.JsonResponse
import net.liftweb.json.JsonAST.JString

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object FileUploadService extends RestHelper {

  serve {
    case request @ "service" :: "fileUpload" :: Nil Post _ => {
      println(request)
      JsonResponse(JString("foobar"))
    }
  }
}