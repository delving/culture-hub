package controllers.api

import controllers.{ ApplicationController, RenderingExtensions }
import play.api.mvc.Action
import core._
import play.api.i18n.Messages
import models.OrganizationConfiguration
import core.collection.OrganizationCollectionMetadata
import com.escalatesoft.subcut.inject.BindingModule

/**
 * Organization API
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class Organization(implicit val bindingModule: BindingModule) extends ApplicationController with RenderingExtensions {

  val organizationCollectionLookupService = inject[OrganizationCollectionLookupService]
  val organizationServiceLocator = inject[DomainServiceLocator[OrganizationService]]

  def providers = OrganizationConfigured {
    Action {
      implicit request =>
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
    }
  }

  def dataProviders = OrganizationConfigured {
    Action {
      implicit request =>
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
    }
  }

  def collections = OrganizationConfigured {
    Action {
      implicit request =>
        val collections = organizationCollectionLookupService.findAll

        val xmlResponse =
          <collections>
            {
              for (c <- collections) yield <collection>
                                             <id>{ toIdentifier(c.spec) }</id>{
                                               c match {
                                                 case metadata: OrganizationCollectionMetadata =>
                                                   <name>
                                                     { metadata.getName }
                                                   </name>
                                                 case _ =>
                                               }
                                             }
                                           </collection>
            }
          </collections>

        DOk(xmlResponse, List("collection"))
    }
  }

  private def getAllOrganiztationCollectionInformation(implicit configuration: OrganizationConfiguration) =
    organizationCollectionLookupService.findAll.flatMap { collection =>
      collection match {
        case metadata: OrganizationCollectionMetadata =>
          Some(metadata)
        case _ =>
          None
      }
    }

  private def toIdentifier(name: String) = name.replaceAll(" ", "_")

}