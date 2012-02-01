package views

import org.bson.types.ObjectId
import play.api.mvc.Request

object Helpers {

  val PAGE_SIZE = 12
  val DEFAULT_THUMBNAIL = "/public/images/dummy-object.png"

  // ~~~ url building

  def getThumbnailUrl(thumbnail: Option[ObjectId], size: Int = 100) = thumbnailUrl(thumbnail, size)

  def thumbnailUrl(thumbnail: Option[ObjectId], size: Int = 100) = thumbnail match {
    case Some(t) => "/thumbnail/%s/%s".format(t, size)
    case None => DEFAULT_THUMBNAIL // TODO now that's not very clean, is it?
  }

  def showErrors(field: String) = ""

}

