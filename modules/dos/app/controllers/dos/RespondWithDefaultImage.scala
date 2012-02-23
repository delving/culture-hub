package controllers.dos

import play.api.mvc._
import play.api.mvc.Results._

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 */

trait RespondWithDefaultImage extends play.api.http.Status {

  def withDefaultFromRequest(result: Result, thumbnail: Boolean = true, width: Option[String], notFoundResponse: Boolean = true)(implicit request: Request[AnyContent]): Result = {

    val emptyFileResult = Ok.sendFile(emptyThumbnailFile, true, f => emptyThumbnailFile.getName)

    def getDefaultImage = {
      if (request.queryString.get("default").isDefined) {
        val defaultImageUrl = request.queryString("default").head

        val defaultImage = (if (thumbnail)
          ImageCache.thumbnail(defaultImageUrl, width, false)(request)
        else
          ImageCache.image(defaultImageUrl, false)(request)).asInstanceOf[SimpleResult[Any]]

        if (defaultImage.header.status == NOT_FOUND) {
          if (notFoundResponse) result else emptyFileResult
        } else {
          defaultImage
        }
      } else if (notFoundResponse) {
        result
      } else {
        emptyFileResult
      }
    }

    if (result.asInstanceOf[SimpleResult[Any]].header.status == NOT_FOUND)
      getDefaultImage
    else
      result
  }
}
