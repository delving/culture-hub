package controllers.organization

import play.api.i18n.Messages
import controllers._
import play.api.mvc.Action
import models.HubUser
import core._
import core.collection.OrganizationCollection
import com.mongodb.casbah.Imports._
import controllers.Token
import com.escalatesoft.subcut.inject.BindingModule

/**
 *
 * @author Gerald de Jong <gerald@delving.eu>
 * @author Manuel Bernhardt <manuel@delving.eu>
 */
class Organizations(implicit val bindingModule: BindingModule) extends DelvingController {

  val harvestCollectionLookupService = inject[HarvestCollectionLookupService]

  def index(language: Option[String]) = OrganizationBrowsing {
    MultitenantAction {
      implicit request =>
        val members: List[HubUser] = HubUser.dao.listOrganizationMembers(configuration.orgId).flatMap(HubUser.dao.findByUsername(_))
        val collections: Seq[OrganizationCollection] = harvestCollectionLookupService.findAllNonEmpty(configuration.orgId, None)
        val lang = language.getOrElse(getLang)
        Ok(Template(
          'orgId -> configuration.orgId,
          'orgName -> organizationServiceLocator.byDomain.getName(configuration.orgId, "en").getOrElse(configuration.orgId),
          'isMember -> HubUser.dao.findByUsername(connectedUser).exists(u => u.organizations.contains(configuration.orgId)),
          'members -> members,
          'collections -> collections,
          'currentLanguage -> lang
        ))
    }
  }

  def listAsTokens(q: String) = Root {
    MultitenantAction {
      implicit request =>
        val tokens = organizationServiceLocator.byDomain.queryByOrgId(q).map { org => Token(org.orgId, org.orgId) }
        Json(tokens)
    }
  }

}