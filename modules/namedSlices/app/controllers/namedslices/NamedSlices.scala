package controllers.namedslices

import play.api.mvc._
import controllers.DelvingController
import com.escalatesoft.subcut.inject.BindingModule
import models.NamedSlice
import models.cms.CMSPage

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class NamedSlices(implicit val bindingModule: BindingModule) extends DelvingController {

  def view(key: String) = Root {
    Action {
      implicit request =>
        NamedSlice.dao.findOneByKey(key) map { slice =>

          val pageContent = CMSPage.dao.findByKeyAndLanguage(slice.cmsPageKey, getLang).headOption.map { page =>
            page.content
          } getOrElse {
            ""
          }

          Ok(Template('pageContent -> pageContent, 'name -> slice.name))
        } getOrElse {
          NotFound(key)
        }
    }
  }

}
