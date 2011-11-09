package controllers

import play.mvc.results.Result
import play.Play

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since 1/2/11 10:09 PM
 */
object Images extends DelvingController {

  def view(image: String): AnyRef = {

    // just for testing
    val smallballs = Play.applicationPath.getAbsolutePath + "/public/images/smallballs.tif"

    // FIXME won't work anymore, image.store.path needs to be rethought

    if (image.isEmpty || image.equalsIgnoreCase("smallballs")) Template('image -> smallballs)
    else Template("/Image/view.html", 'image -> (Play.configuration.getProperty("image.store.path") + "/" + image))

  }

  def iipsrv(): Result = {
    // TODO this should be a permanent redirect
    Redirect(Play.configuration.getProperty("iipsrv.url") + "?" + request.querystring, true)
  }

}