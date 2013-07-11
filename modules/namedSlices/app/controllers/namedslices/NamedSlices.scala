package controllers.namedslices

import play.api.mvc._
import controllers.DelvingController
import com.escalatesoft.subcut.inject.BindingModule
import models.NamedSlice
import models.cms.CMSPage
import controllers.search.SearchResults

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class NamedSlices(implicit val bindingModule: BindingModule) extends DelvingController with SearchResults {

  def view(key: String) = Root {
    MultitenantAction {
      implicit request =>
        NamedSlice.dao.findOnePublishedByKey(key) map { slice =>

          val pageContent = CMSPage.dao.findByKeyAndLanguage(slice.cmsPageKey, getLang.language).headOption.map { page =>
            page.content
          } getOrElse {
            ""
          }

          Ok(Template('pageContent -> pageContent, 'name -> slice.name, 'key -> slice.key))
        } getOrElse {
          NotFound(key)
        }
    }
  }

  def search(key: String, query: String): Action[AnyContent] = Root {
    MultitenantAction {
      implicit request =>
        NamedSlice.dao.findOnePublishedByKey(key) map { slice =>
          searchResults(query, slice.query.toQueryFilter, s"/slices/${slice.key}/search")
        } getOrElse {
          NotFound(key)
        }
    }
  }

}
