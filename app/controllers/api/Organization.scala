package controllers.api

import controllers.{ ApplicationController, RenderingExtensions, OrganizationConfigurationAware }
import play.api.mvc.Action
import core._
import play.api.i18n.Messages
import models.OrganizationConfiguration
import core.collection.OrganizationCollectionInformation

/**
 * Organization API
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class Organization extends ApplicationController with OrganizationConfigurationAware with RenderingExtensions {

  val organizationCollectionLookupService = inject[OrganizationCollectionLookupService]
  val organizationServiceLocator = inject[DomainServiceLocator[OrganizationService]]

  def providers(orgId: String) = OrganizationConfigured {
    Action {
      implicit request =>
        if (organizationServiceLocator.byDomain.exists(orgId)) {

          val providers = getAllOrganiztationCollectionInformation.map(_.getProvider)

          val xmlResponse =
            <providers>
              {
                for (p <- providers) yield <provider>
                                             <id>{ toIdentifier(p) }</id>
                                             <name>{ p }</name>
                                           </provider>
              }
            </providers>

          DOk(xmlResponse, List("providers"))

        } else {
          NotFound(Messages("hub.CouldNotFindOrganization", orgId))
        }
    }
  }

  def dataProviders(orgId: String) = OrganizationConfigured {
    Action {
      implicit request =>
        if (organizationServiceLocator.byDomain.exists(orgId)) {

          val dataProviders = getAllOrganiztationCollectionInformation.map(_.getDataProvider)

          val xmlResponse =
            <dataProviders>
              {
                for (p <- dataProviders) yield <dataProvider>
                                                 <id>{ toIdentifier(p) }</id>
                                                 <name>{ p }</name>
                                               </dataProvider>
              }
            </dataProviders>

          DOk(xmlResponse, List("dataProviders"))

        } else {
          NotFound(Messages("hub.CouldNotFindOrganization", orgId))
        }
    }
  }

  def collections(orgId: String) = OrganizationConfigured {
    Action {
      implicit request =>
        if (organizationServiceLocator.byDomain.exists(orgId)) {
          val collections = organizationCollectionLookupService.findAll

          val xmlResponse =
            <collections>
              {
                for (c <- collections) yield <collection>
                                               <id>{ toIdentifier(c.spec) }</id>{
                                                 if (c.isInstanceOf[OrganizationCollectionInformation]) {
                                                   <name>{ c.asInstanceOf[OrganizationCollectionInformation].getName }</name>
                                                 }
                                               }
                                             </collection>
              }
            </collections>

          DOk(xmlResponse, List("collection"))

        } else {
          NotFound(Messages("hub.CouldNotFindOrganization", orgId))
        }
    }
  }

  private def getAllOrganiztationCollectionInformation(implicit configuration: OrganizationConfiguration) = organizationCollectionLookupService.findAll.flatMap { collection =>
    if (collection.isInstanceOf[OrganizationCollectionInformation]) {
      Some(collection.asInstanceOf[OrganizationCollectionInformation])
    } else {
      None
    }
  }

  private def toIdentifier(name: String) = name.replaceAll(" ", "_")

}