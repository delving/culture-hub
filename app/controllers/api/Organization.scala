package controllers.api

import controllers.RenderingExtensions
import play.api.mvc.{Controller, Action}
import core.{DomainConfigurationAware, HubServices}
import play.api.i18n.Messages
import models.DataSet

/**
 * Organization API
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Organization extends Controller with DomainConfigurationAware with RenderingExtensions {

  def providers(orgId: String) = DomainConfigured {
    Action {
      implicit request =>
        if (HubServices.organizationService(configuration).exists(orgId)) {

          val providers = getFactAlternatives(orgId, "provider")

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

          val dataProviders = getFactAlternatives(orgId, "dataProvider")

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
          val collections = models.DataSet.dao.findAllByOrgId(orgId)

          val xmlResponse =
            <collections>
              {for (c <- collections) yield
              <collection>
                <id>{toIdentifier(c.spec)}</id>
                <name>{c.getName}</name>
              </collection>}
            </collections>

          DOk(xmlResponse, List("collection"))

        } else {
          NotFound(Messages("organizations.organization.orgNotFound", orgId))
        }
    }
  }

  private def getFactAlternatives(orgId: String, fact: String) = DataSet.dao(orgId).findAll(orgId).map(ds => ds.details.facts.get(fact)).filterNot(_ == null).map(_.toString).toList.distinct

  private def toIdentifier(name: String) = name.replaceAll(" ", "_")


}
