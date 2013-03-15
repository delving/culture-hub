package controllers.dos

import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.iteratee.Enumerator
import play.api.Play

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 */

trait RespondWithDefaultImage extends play.api.http.Status {

  def withDefaultFromRequest(result: Result, thumbnail: Boolean = true, width: Option[String], notFoundResponse: Boolean = true)(implicit request: Request[AnyContent]): Result = {

    val fileContent: Option[Enumerator[Array[Byte]]] = Play.current.resourceAsStream(emptyThumbnailPath).map(Enumerator.fromStream(_))
    val emptyFileResult = fileContent.map(c => SimpleResult(header = ResponseHeader(200), body = c)).getOrElse(NotFound)

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

    val notFound = if (result.isInstanceOf[SimpleResult[_]]) {
      result.asInstanceOf[SimpleResult[Any]].header.status == NOT_FOUND
    } else if (result.isInstanceOf[ChunkedResult[_]]) {
      result.asInstanceOf[ChunkedResult[Any]].header.status == NOT_FOUND
    } else {
      // let's hope for the best
      false
    }

    if (notFound)
      getDefaultImage
    else
      result
  }
}
