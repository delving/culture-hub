package controllers.api

import controllers.{DomainConfigurationAware, RenderingExtensions}
import play.api.mvc.{Controller, Action}
import core.{OrganizationCollectionLookupService, HubModule, HubServices}
import play.api.i18n.Messages
import models.DomainConfiguration
import core.collection.OrganizationCollectionInformation

/**
 * Organization API
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Organization extends Controller with DomainConfigurationAware with RenderingExtensions {
  
  val organizationCollectionLookupService = HubModule.inject[OrganizationCollectionLookupService](name = None)

  def providers(orgId: String) = DomainConfigured {
    Action {
      implicit request =>
        if (HubServices.organizationService(configuration).exists(orgId)) {

          val providers = getAllOrganiztationCollectionInformation.map(_.getProvider)

          val xmlResponse =
            <providers>
              {for (p <- providers) yield
              <provider>
                <id>{toIdentifier(p)}</id>
                <name>{p}</name>
              </provider>}
            </providers>

          DOk(xmlResponse, List("providers"))

        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

  def dataProviders(orgId: String) = DomainConfigured {
    Action {
      implicit request =>
        if (HubServices.organizationService(configuration).exists(orgId)) {

          val dataProviders = getAllOrganiztationCollectionInformation.map(_.getDataProvider)

          val xmlResponse =
            <dataProviders>
              {for (p <- dataProviders) yield
              <dataProvider>
                <id>{toIdentifier(p)}</id>
                <name>{p}</name>
              </dataProvider>}
            </dataProviders>

          DOk(xmlResponse, List("dataProviders"))

        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

  def collections(orgId: String) = DomainConfigured {
    Action {
      implicit request =>
        if (HubServices.organizationService(configuration).exists(orgId)) {
          val collections = organizationCollectionLookupService.findAll

          val xmlResponse =
            <collections>
              {for (c <- collections) yield
              <collection>
                <id>{toIdentifier(c.spec)}</id>{if (c.isInstanceOf[OrganizationCollectionInformation]) {
                <name>{c.asInstanceOf[OrganizationCollectionInformation].getName}</name>}}
              </collection>}
            </collections>

          DOk(xmlResponse, List("collection"))

        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

  private def getAllOrganiztationCollectionInformation(implicit configuration: DomainConfiguration) = organizationCollectionLookupService.findAll.flatMap { collection =>
    if(collection.isInstanceOf[OrganizationCollectionInformation]) {
      Some(collection.asInstanceOf[OrganizationCollectionInformation])
    } else {
      None
    }
  }

  private def toIdentifier(name: String) = name.replaceAll(" ", "_")


}
