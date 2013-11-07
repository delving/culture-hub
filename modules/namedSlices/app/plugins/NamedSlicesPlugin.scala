package plugins

import play.api.Application
import core.{ MenuElement, MainMenuEntry, CultureHubPlugin }
import models.{ Role, NamedSlice, OrganizationConfiguration }

class NamedSlicesPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "namedSlices"

  override def organizationMenuEntries(configuration: OrganizationConfiguration, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "namedSlice",
      titleKey = "namedslice.namedSlices",
      roles = Seq(Role.OWN),
      items = Seq(
        MenuElement("/admin/namedSlices", "hub.List"),
        MenuElement("/admin/namedSlices/add", "hub.Create")
      )
    )
  )

  override def mainMenuEntries(configuration: OrganizationConfiguration, lang: String): Seq[MainMenuEntry] = {
    val slices = NamedSlice.dao(configuration.orgId).findAllForMainMenu

    slices map { slice =>
      MainMenuEntry(
        key = slice.key,
        titleKey = slice.name,
        mainEntry = Some(MenuElement(url = "/slices/" + slice.key, titleKey = slice.name))
      )
    }
  }
}

