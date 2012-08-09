package plugins

import play.api.Application
import core.CultureHubPlugin
import models._
import core.collection.HarvestCollectionLookup
import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import core.MainMenuEntry
import models.UserProfile
import scala.Some
import core.MenuElement


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class CorePlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "core"

  private val dataSetHarvestCollectionLookup = new DataSetLookup

  override def enabled: Boolean = true


  override def isEnabled(configuration: DomainConfiguration): Boolean = true

  override def mainMenuEntries(implicit configuration: DomainConfiguration, lang: String): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "home",
      titleKey = "site.nav.home",
      mainEntry = Some(MenuElement(url = "/", titleKey = "site.nav.home"))
    )
  )

  override def organizationMenuEntries(orgId: String, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "overview",
      titleKey = "ui.label.overview",
      mainEntry = Some(MenuElement("/organizations/" + orgId, "ui.label.overview")),
      membersOnly = false
    ),
    MainMenuEntry(
      key = "administration",
      titleKey = "ui.label.administration",
      mainEntry = Some(MenuElement("/organizations/%s/admin".format(orgId), "ui.label.administration")),
      roles = Seq(Role.OWN)
    ),
    MainMenuEntry(
      key = "groups",
      titleKey = "thing.groups",
      items = Seq(
        MenuElement("/organizations/%s/groups".format(orgId), "org.group.list"),
        MenuElement("/organizations/%s/groups/create".format(orgId), "org.group.create", Seq(Role.OWN))
      )
    )
  )

  override def harvestCollectionLookups: Seq[HarvestCollectionLookup] = Seq(dataSetHarvestCollectionLookup)

  /**
   * Executed when test data is loaded (for development and testing)
   */
  override def onLoadTestData() {
    if (HubUser.dao("delving").count() == 0) bootstrapUser()
    if (Group.dao("delving").count() == 0) bootstrapAccessControl()

    def bootstrapUser() {
      val profile = UserProfile()
      HubUser.dao("delving").insert(new HubUser(
        _id = new ObjectId("4e5679a80364ae80333ab939"),
        userName = "bob",
        firstName = "bob",
        lastName = "Marley",
        email = "bob@gmail.com",
        userProfile = profile
      ))
      HubUser.dao("delving").insert(new HubUser(
        _id = new ObjectId("4e5679a80364ae80333ab93a"),
        userName = "jimmy",
        firstName = "Jimmy",
        lastName = "Hendrix",
        email = "jimmy@gmail.com",
        userProfile = profile
      ))
      HubUser.dao("delving").insert(new HubUser(
        _id = new ObjectId("4e5679a80364ae80333ab93b"),
        userName = "dan",
        firstName = "Dan",
        lastName = "Brown",
        email = "dan@gmail.com",
        userProfile = profile
      ))
    }

    def bootstrapAccessControl() {

      // all users are in delving
      HubUser.dao("delving").find(MongoDBObject()).foreach(u => HubUser.dao("delving").addToOrganization(u.userName, "delving"))

    }

  }
}