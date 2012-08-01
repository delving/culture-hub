package controllers.search

import play.api.mvc._
import controllers.DelvingController
import scala.collection.JavaConverters._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object AdvancedSearch extends DelvingController {

  def advancedSearch = Root {
    Action {
      implicit request =>
        val providers = List("Provider 1", "Provider 2", "Provider 42" )
        Ok(Template('providers -> providers.asJava))
    }
  }

}
