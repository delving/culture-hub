package controllers.ead

import controllers.DelvingController
import play.api.mvc._
import play.api._
import play.api.Play.current
import scala.xml._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object Prototype extends DelvingController {

  def sampleData = Action {
    implicit request =>

      Play.resourceAsStream("mildred_davenport.xml") map { resourceStream =>
        {
          val source = Source.fromInputStream(resourceStream)
          val xml = scala.xml.XML.load(source)
          Ok(util.Json.toJson(xml))
        }
      } getOrElse {
        InternalServerError("Couldn't find test resource")
      }

  }

}
