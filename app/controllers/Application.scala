package controllers

import play.api.mvc._
import models._
import cms.CMSPage
import com.mongodb.casbah.Imports._
import core.{RequestContext, CultureHubPlugin, ThemeInfo}
import core.Constants._
import core.indexing.IndexField._

object Application extends DelvingController {


  def index = Root {
    Action {
      implicit request =>
        val themeInfo = renderArgs("themeInfo").get.asInstanceOf[ThemeInfo]
        val recentMdrs: Seq[ListItem] = try {
          CommonSearch.search(
            None,
            List("%s:%s AND %s:%s".format(RECORD_TYPE.key, ITEM_TYPE_MDR, HAS_DIGITAL_OBJECT.key, true))
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

  def page(key: String) = Root {
    Action {
      implicit request =>
      // TODO link the themes to the organization so this also works on multi-org hubs
        CMSPage.dao.find(MongoDBObject("key" -> key, "lang" -> getLang, "orgId" -> configuration.orgId)).$orderby(MongoDBObject("_id" -> -1)).limit(1).toList.headOption match {
          case None => NotFound(key)
          case Some(page) => Ok(Template('page -> page))
        }
    }
  }

  def notFound(what: String) = Action {
    implicit request => Results.NotFound(what)
  }


}