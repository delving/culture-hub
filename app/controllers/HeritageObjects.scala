package controllers

import play.api.mvc._
import core.Constants._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object HeritageObjects extends DelvingController {

  def list(page: Int) = Root {
    Action {
      implicit request =>
        val browser: (List[ListItem], Int) = Search.browse(MDR, None, page, theme)
        Ok(Template("/list.html", 'title -> listPageTitle("mdr"), 'itemName -> MDR, 'items -> browser._1, 'page -> page, 'count -> browser._2))
    }
  }

}
