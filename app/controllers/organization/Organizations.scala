package controllers.organization

import play.api.i18n.Messages
import controllers._
import core.HubServices
import play.api.mvc.Action
import models.{Visibility, DataSet, HubUser}

/**
 *
 * @author Gerald de Jong <gerald@delving.eu>
 * @author Manuel Bernhardt <manuel@delving.eu>
 */

object Organizations extends DelvingController {

  def index(orgId: String, language: Option[String]) = Root {
    Action {
      implicit request =>
        if (HubServices.organizationService.exists(orgId)) {
          val members: List[ListItem] = HubUser.listOrganizationMembers(orgId).flatMap(HubUser.findByUsername(_))
          val dataSets: List[ShortDataSet] = DataSet.findAllVisible(orgId, connectedUser, request.session(AccessControl.ORGANIZATIONS))
          val lang = language.getOrElse(getLang)
          Ok(Template(
            'orgId -> orgId,
            'orgName -> HubServices.organizationService.getName(orgId, "en").getOrElse(orgId),
            'isMember -> HubUser.findByUsername(connectedUser).map(u => u.organizations.contains(orgId)).getOrElse(false),
            'members -> members,
            'dataSets -> dataSets,
            'isOwner -> HubServices.organizationService.isAdmin(orgId, connectedUser),
            'currentLanguage -> lang

          ))
        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

  def providers(orgId: String) = Root {
    Action {
      implicit request =>
        if (HubServices.organizationService.exists(orgId)) {

          val providers = getFactAlternatives(orgId, "provider")

          val xmlResponse =
            <providers>
              {for (p <- providers) yield
              <provider>
                <id>
                  {toIdentifier(p)}
                </id>
                <name>
                  {p}
                </name>
              </provider>}
            </providers>

          DOk(xmlResponse, "provider")

        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

  def dataProviders(orgId: String) = Root {
    Action {
      implicit request =>
        if (HubServices.organizationService.exists(orgId)) {

          val dataProviders = getFactAlternatives(orgId, "dataProvider")

          val xmlResponse =
            <dataProviders>
              {for (p <- dataProviders) yield
              <dataProvider>
                <id>
                  {toIdentifier(p)}
                </id>
                <name>
                  {p}
                </name>
              </dataProvider>}
            </dataProviders>

          DOk(xmlResponse, "dataProvider")

        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

  def collections(orgId: String) = Root {
    Action {
      implicit request =>
        if (HubServices.organizationService.exists(orgId)) {
          val collections = models.DataSet.findAllByOrgId(orgId).filter(_.visibility == Visibility.PUBLIC)

          val xmlResponse =
            <collections>
              {for (c <- collections) yield
              <collection>
                <id>
                  {toIdentifier(c.spec)}
                </id>
                <name>
                  {c.name}
                </name>
              </collection>}
            </collections>

          DOk(xmlResponse, "collection")

        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

  private def getFactAlternatives(orgId: String, fact: String) = DataSet.findAll(orgId).map(ds => ds.details.facts.get(fact)).filterNot(_ == null).map(_.toString).toList.distinct

  private def toIdentifier(name: String) = name.replaceAll(" ", "_")

}