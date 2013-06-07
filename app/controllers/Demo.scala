package controllers

import play.api.mvc._
import play.api.Play
import play.api.Play.current
import play.api.mvc.Results._
import com.escalatesoft.subcut.inject.BindingModule

/**
 * Various things, for demonstration purposes
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class Demo(implicit val bindingModule: BindingModule) extends DelvingController {

  /**
   * DeepZoom viewer
   */
  def view(image: Option[String]) = Root {
    Action {
      implicit request =>
        val smallballs = current.getFile("/public/images/smallballs.tif").getAbsolutePath
        if (image.isEmpty)
          Ok(Template('image -> smallballs))
        else
          Ok(Template('image -> (image.get)))
    }
  }

  def iipsrv = Root {
    Action {
      implicit request =>
        Redirect(Play.configuration.getString("iipsrv.url").getOrElse("") + "?" + request.rawQueryString, MOVED_PERMANENTLY)
    }
  }

  def yumaImage = Root {
    Action {
      implicit request =>
        Ok(Template())
    }
  }

  def yumaMap = Root {
    Action {
      implicit request =>
        Ok(Template())
    }
  }

}