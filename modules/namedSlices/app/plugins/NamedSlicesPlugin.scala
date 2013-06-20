package plugins

import play.api.Application
import core.{ MenuElement, MainMenuEntry, CultureHubPlugin }
import models.{ NamedSlice, OrganizationConfiguration }

class NamedSlicesPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "namedSlices"

  override def mainMenuEntries(configuration: OrganizationConfiguration, lang: String): Seq[MainMenuEntry] = {
    val slices = NamedSlice.dao(configuration.orgId).findAllPublished

    slices map { slice =>
      MainMenuEntry(
        key = slice.key,
        titleKey = slice.name,
        mainEntry = Some(MenuElement(url = "/slice/" + slice.key, titleKey = slice.name))
      )
    }
  }
}

