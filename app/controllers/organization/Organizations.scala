package controllers.organization

import play.api.i18n.Messages
import controllers._
import play.api.mvc.Action
import models.HubUser
import core.{OrganizationCollectionLookupService, HubModule}
import core.collection.OrganizationCollection
import com.mongodb.casbah.Imports._
import controllers.Token
import java.util.regex.Pattern

/**
 *
 * @author Gerald de Jong <gerald@delving.eu>
 * @author Manuel Bernhardt <manuel@delving.eu>
 */

object Organizations extends BoundController(HubModule) with Organizations

trait Organizations extends DelvingController { this: BoundController =>

  val organizationCollectionLookupService = inject[OrganizationCollectionLookupService]

  def index(orgId: String, language: Option[String]) = OrganizationBrowsing {
    Action {
      implicit request =>
        if (organizationServiceLocator.byDomain.exists(orgId)) {
          val members: List[HubUser] = HubUser.dao.listOrganizationMembers(orgId).flatMap(HubUser.dao.findByUsername(_))
          val collections: Seq[OrganizationCollection] = organizationCollectionLookupService.findAll
          val lang = language.getOrElse(getLang)
          Ok(Template(
            'orgId -> orgId,
            'orgName -> organizationServiceLocator.byDomain.getName(orgId, "en").getOrElse(orgId),
            'isMember -> HubUser.dao.findByUsername(connectedUser).map(u => u.organizations.contains(orgId)).getOrElse(false),
            'members -> members,
            'collections -> collections,
            'currentLanguage -> lang

          ))
        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

}