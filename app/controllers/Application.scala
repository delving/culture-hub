package controllers

import play.api.mvc._
import core.{ RequestContext, CultureHubPlugin, ThemeInfo }
import core.Constants._
import core.indexing.IndexField._
import com.escalatesoft.subcut.inject.BindingModule

class Application(implicit val bindingModule: BindingModule) extends DelvingController {

  def index = Root {
    Action {
      implicit request =>
        val themeInfo = renderArgs("themeInfo").get.asInstanceOf[ThemeInfo]
        val recentMdrs: Seq[ListItem] = try {
          searchServiceLocator.byDomain.search(
            None,
            List("%s:%s AND %s:%s".format(RECORD_TYPE.key, ITEM_TYPE_MDR, HAS_DIGITAL_OBJECT.key, true)),
            request.queryString,
            request.host
          )._1.slice(0, themeInfo.themeProperty("recentMdrsCount", classOf[Int]))

        } catch {
          case t: Throwable =>
            List.empty
        }

        val pluginSnippets = CultureHubPlugin.getEnabledPlugins.flatMap(_.homePageSnippet)

        val pluginIncludes = pluginSnippets.map(_._1).toSeq

        pluginSnippets.foreach { snippet =>
          snippet._2(RequestContext(request, configuration, renderArgs(), getLang))
        }

        Ok(Template('recentMdrs -> recentMdrs, 'pluginIncludes -> pluginIncludes))
    }
  }

  def notFound(what: String) = Action {
    implicit request => Results.NotFound(what)
  }

}