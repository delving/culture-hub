package plugins

import play.api.mvc.Handler
import scala.util.matching.Regex
import models.PortalTheme
import core.{MenuElement, MainMenuEntry, CultureHubPlugin}
import play.api.Application

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class MusipPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "musip"

  override val routes: Map[(String, Regex), List[String] => Handler] = Map(
    ("GET", """^/([A-Za-z0-9-]+)/collection/([A-Za-z0-9-]+)$""".r) -> {
      pathArgs: List[String] => controllers.musip.Show.collection(pathArgs(0), pathArgs(1))
    },
    ("GET", """^/([A-Za-z0-9-]+)/museum/([A-Za-z0-9-]+)$""".r) -> {
      pathArgs: List[String] => controllers.musip.Show.museum(pathArgs(0), pathArgs(1))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/admin/musip/synchronize$""".r) -> {
      pathArgs: List[String] => controllers.musip.Admin.synchronize(pathArgs(0))
    })

  override def mainMenuEntries(theme: PortalTheme, lang: String): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "museums",
      titleKey = "plugin.musip.museums",
      mainEntry = Some(MenuElement(url = "/search?query=*:*&qf=delving_recordType_facet:museum", titleKey = "plugin.musip.museums"))
    ),
    MainMenuEntry(
      key = "collections",
      titleKey = "plugin.musip.collections",
      mainEntry = Some(MenuElement(url = "/search?query=*:*&qf=delving_recordType_facet:collection", titleKey = "plugin.musip.collections"))
    )
  )
}
