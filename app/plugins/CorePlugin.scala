package plugins

import play.api.Application
import core.{MenuEntry, OrganizationMenuEntry, CultureHubPlugin}
import models.GrantType


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CorePlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "core"

  override def enabled: Boolean = true

  override def organizationMenuEntries(context: Map[String, String], roles: Seq[String]): Seq[OrganizationMenuEntry] = Seq(
    OrganizationMenuEntry(
      key = "overview",
      titleKey = "ui.label.overview",
      mainEntry = Some(MenuEntry("/organizations/" + context("orgId"), "ui.label.overview")),
      membersOnly = false
    ),
    OrganizationMenuEntry(
      key = "groups",
      titleKey = "thing.groups",
      items = Seq(
        MenuEntry("/organizations/%s/groups".format(context("orgId")), "org.group.list"),
        MenuEntry("/organizations/%s/groups/create".format(context("orgId")), "org.group.create", Seq(GrantType.OWN))
      )
    ),
    OrganizationMenuEntry(
      key = "datasets",
      titleKey = "thing.datasets",
      items = Seq(
        MenuEntry("/organizations/%s/dataset".format(context("orgId")), "organization.dataset.list"),
        MenuEntry("/organizations/%s/dataset/statistics".format(context("orgId")), "org.stats", Seq(GrantType.OWN)),
        MenuEntry("/organizations/%s/dataset/add".format(context("orgId")), "organization.dataset.create", Seq(GrantType.OWN))
      )
    ),
    OrganizationMenuEntry(
      key = "virtual-collections",
      titleKey = "thing.virtualCollections",
      items = Seq(
        MenuEntry("/organizations/%s/virtualCollection".format(context("orgId")), "org.vc.list"),
        MenuEntry("/organizations/%s/dataset/add".format(context("orgId")), "org.vc.new", Seq(GrantType.OWN))
      )
    ),
    OrganizationMenuEntry(
      key = "sipcreator",
      titleKey = "ui.label.sipcreator",
      mainEntry = Some(MenuEntry("/organizations/%s/sip-creator".format(context("orgId")), "ui.label.sipcreator"))
    )
  )
}

/*


*/