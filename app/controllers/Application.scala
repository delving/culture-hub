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

  /**
   * Permanent redirection for legacy routes of the kind /organizations/:orgId/...
   */
  def legacyOrganizationsPath(path: String, orgId: String = "fooBar") = Action {
    implicit request =>

      val url = path.split("/").toList match {
        case "api" :: Nil => s"/api"
        case "api" :: tail => s"/api/${tail.mkString("/")}"

        case "proxy" :: "list" :: Nil => s"/api/proxy/list"
        case "proxy" :: proxyKey :: "search" :: Nil => s"/api/proxy/$proxyKey/search"
        case "proxy" :: proxyKey :: "item" :: itemKey => s"/api/proxy/$proxyKey/item/${itemKey.mkString("/")}"

        case "statistics" :: Nil => "/statistics"

        case "search" :: Nil => "/api/search"

        case "root" :: Nil => "/admin"

        case _ => s"/admin/$path"
      }

      Redirect(url, request.queryString, MOVED_PERMANENTLY)
  }

}