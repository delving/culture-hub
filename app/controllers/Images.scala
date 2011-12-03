package controllers

import play.mvc.results.Result
import play.Play

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @since 1/2/11 10:09 PM
 */
object Images extends DelvingController {

  def view: AnyRef = {

    val image = params.get("image")

    // just for testing
    val smallballs = Play.applicationPath.getAbsolutePath + "/public/images/smallballs.tif"

    if (image.isEmpty || image.equalsIgnoreCase("smallballs")) Template('image -> smallballs)
    else Template("/Image/view.html", 'image -> (image))

  }

  def iipsrv(): Result = {
    // TODO this should be a permanent redirect
    Redirect(Play.configuration.getProperty("iipsrv.url") + "?" + request.querystring, true)
  }

}