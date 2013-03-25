package plugins

import play.api.Application
import core.CultureHubPlugin
import collection.immutable.ListMap
import scala.util.matching.Regex
import play.api.mvc.Handler
import controllers.api.IndexItemOrganizationCollectionLookupService

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class IndexApiPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "indexApi"

  /*
  GET         /organizations/:orgId/api/index                                   controllers.api.Index.status(orgId)
  POST        /organizations/:orgId/api/index                                   controllers.api.Index.submit(orgId)
  POST        /organizations/:orgId/api/reIndex                                 controllers.api.Index.reIndex(orgId)

   */
  override val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap(
    ("GET", """^/organizations/([A-Za-z0-9-]+)/api/index""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.api.Index.status(pathArgs(0))
    },
    ("POST", """^/organizations/([A-Za-z0-9-]+)/api/index""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.api.Index.submit(pathArgs(0))
    },
    ("POST", """^/organizations/([A-Za-z0-9-]+)/api/reIndex""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) =>
        controllers.api.Index.reIndex()
    }
  )

  /**
   * Service instances this plugin provides
   */
  override def services: Seq[Any] = Seq(
    new IndexItemOrganizationCollectionLookupService
  )
}
