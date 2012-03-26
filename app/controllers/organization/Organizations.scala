package controllers.organization

import play.api.mvc.Action
import models.{Visibility, DataSet, HubUser}
import play.api.i18n.Messages
import controllers._
import core.HubServices

/**
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

object Organizations extends DelvingController {

  def index(orgId: String) = Root {
    Action {
      implicit request =>
        if(HubServices.organizationService.exists(orgId)) {
            val members: List[ListItem] = HubUser.listOrganizationMembers(orgId).flatMap(HubUser.findByUsername(_))
            val dataSets: List[ShortDataSet] =
              DataSet.findAllCanSee(orgId, connectedUser).
                filter(ds =>
                  ds.visibility == Visibility.PUBLIC ||
                  (
                    ds.visibility == Visibility.PRIVATE &&
                    session.get(AccessControl.ORGANIZATIONS) != null &&
                    request.session(AccessControl.ORGANIZATIONS).split(",").contains(orgId)
                  )
            ).toList
            Ok(Template(
              'orgId -> orgId,
              'orgName -> HubServices.organizationService.getName(orgId, "en").getOrElse(orgId),
              'isMember -> HubUser.findByUsername(connectedUser).map(u => u.organizations.contains(orgId)).getOrElse(false),
              'members -> members,
              'dataSets -> dataSets,
              'isOwner -> HubServices.organizationService.isAdmin(orgId, connectedUser)
            ))
        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }
  
  def providers(orgId: String) = Root {
    Action {
      implicit request =>
        if(HubServices.organizationService.exists(orgId)) {

          val providers = getFactAlternatives(orgId, "provider")
          
          val xmlResponse =
            <providers>
              {for (p <- providers) yield
              <provider>
                <id>{toIdentifier(p)}</id>
                <name>{p}</name>
              </provider>
              }
            </providers>
          
          Ok(xmlResponse)
          
        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }        
    }
  }

  def dataProviders(orgId: String) = Root {
    Action {
      implicit request =>
        if(HubServices.organizationService.exists(orgId)) {

          val dataProviders = getFactAlternatives(orgId, "dataProvider")

          val xmlResponse =
            <dataProviders>
              {for (p <- dataProviders) yield
              <dataProvider>
                <id>{toIdentifier(p)}</id>
                <name>{p}</name>
              </dataProvider>
              }
            </dataProviders>

          Ok(xmlResponse)

        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }
  
  private def getFactAlternatives(orgId: String, fact: String) = DataSet.findAll(orgId).map(ds => ds.details.facts.get(fact)).filterNot(_ == null).map(_.toString).toList.distinct

  private def toIdentifier(name: String) = name.replaceAll(" ", "_")

}