package controllers

import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import models.OrganizationConfiguration
import play.api.i18n.Lang


/**
 * Breadcrumb computation based on URL. Context data is passed in through a map of maps, the inner map containing (url, label)
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Breadcrumbs {

  def crumble(p: Map[String, Map[String, String]] = Map.empty)(implicit configuration: OrganizationConfiguration, lang: Lang, request: RequestHeader): List[((String, String), Int)] = {

    // we can't make the difference between orgId/object and user/object
    val crumbList = if (p != null && p.contains(core.Constants.IN_ORGANIZATION)) {
      "org" :: request.path.split("/").drop(1).toList
    } else {
      request.path.split("/").drop(1).toList
    }

    val crumbs = crumbList match {

      case "users" :: Nil => List(("/users", Messages("thing.users")))

      case "search" :: Nil => List(("NOLINK", Messages("ui.label.search")))

      case "org" :: orgId :: "thing" :: spec :: recordId :: Nil =>
        val returnToResults = Option(p.get("search").get("returnToResults"))
        returnToResults match {
          case Some(r) if r.length() > 0 => List(("NOLINK", Messages("ui.label.search")), ("/search?" + r, "%s".format(p.get("search").get("searchTerm"))), ("NOLINK", p.get("title").get("label")))
          case _ => List(("/organizations/" + orgId, orgId), ("NOLINK", Messages("thing.objects")), ("NOLINK", spec), ("NOLINK", p.get("title").get("label")))
        }

      // TODO fetch these crumbs from the plugin

      case "org" :: orgId :: "museum" :: id :: Nil =>
        val returnToResults = Option(p.get("search").get("returnToResults"))
        returnToResults match {
          case Some(r) if r.length() > 0 => List(("NOLINK", Messages("ui.label.search")), ("/search?" + r, "%s".format(p.get("search").get("searchTerm"))), ("NOLINK", p.get("title").get("label")))
          case _ => List(("/organizations/" + orgId, orgId), ("NOLINK", Messages("plugin.musip.museums")), ("NOLINK", p.get("title").get("label")))
        }

      case "org" :: orgId :: "collection" :: id :: Nil =>
        val returnToResults = Option(p.get("search").get("returnToResults"))
        returnToResults match {
          case Some(r) if r.length() > 0 => List(("NOLINK", Messages("ui.label.search")), ("/search?" + r, "%s".format(p.get("search").get("searchTerm"))), ("NOLINK", p.get("title").get("label")))
          case _ => List(("/organizations/" + orgId, orgId), ("NOLINK", Messages("plugin.musip.collections")), ("NOLINK", p.get("title").get("label")))
        }

      case "rijks" :: "search" :: Nil => List(("/rijks", Messages("plugin.rijks.rijks")), ("NOLINK", Messages("ui.label.search")))


      case "organizations" :: orgName :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName))
      case "organizations" :: orgName :: "admin" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/admin", Messages("org.admin.index.title")))
      case "organizations" :: orgName :: "dataset" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/dataset", Messages("thing.datasets")))
      case "organizations" :: orgName :: "dataset" :: "add" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/dataset", Messages("organization.dataset.create")))
      case "organizations" :: orgName :: "dataset" :: name :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/dataset", Messages("thing.datasets")), ("/organizations/" + orgName + "/dataset" + name, name))
      case "organizations" :: orgName :: "dataset" :: name :: "update" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/dataset", Messages("thing.datasets")), ("/organizations/" + orgName + "/dataset/" + name, name), ("/organizations/" + orgName + "/dataset/" + name + "/update", Messages("ui.label.edit")))
      case "organizations" :: orgName :: "groups" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/groups", Messages("thing.groups")))
      case "organizations" :: orgName :: "groups" :: "create" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/groups", Messages("thing.groups")), ("NOLINK", Messages("ui.label.create")))
      case "organizations" :: orgName :: "groups" :: "update" :: id :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/groups", Messages("thing.groups")), ("/organizations/" + orgName + "/groups/update/" + id, Messages("ui.label.edit")))
      case "organizations" :: orgName :: "sip-creator" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/sip-creator", Messages("ui.label.sipcreator")))
      case "organizations" :: orgName :: "site" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", Messages("plugin.cms")), ("NOLINK", Messages("locale." + request.session.get("lang").getOrElse(configuration.ui.defaultLanguage))))
      case "organizations" :: orgName :: "site" :: "upload" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", Messages("plugin.cms")), ("NOLINK", Messages("plugin.cms.upload")))
      case "organizations" :: orgName :: "site" :: lang :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", Messages("plugin.cms")), ("NOLINK", Messages("locale." + lang)))
      case "organizations" :: orgName :: "site" :: lang :: "page" :: "add" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", Messages("plugin.cms")), ("/organizations/" + orgName + "/site/" + lang, Messages("locale." + lang)), ("NOLINK", Messages("plugin.cms.page.create")))
      case "organizations" :: orgName :: "site" :: lang :: "page" :: page :: "update" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", Messages("plugin.cms")), ("/organizations/" + orgName + "/site/" + lang, Messages("locale." + lang)), ("NOLINK", Messages("plugin.cms.page.update") + " \"" + page + "\""))
      case "organizations" :: orgName :: "virtualCollection" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/virtualCollection", Messages("thing.virtualCollections")))
      case "organizations" :: orgName :: "virtualCollection" :: "add" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/virtualCollection", Messages("thing.virtualCollections")), ("/organizations/" + orgName + "/virtualCollection/add", Messages("org.vc.new")))

      case user :: Nil => List(("/" + user, user))

      case _ => List()
    }
    (("/", Messages("site.nav.home")) :: crumbs).zipWithIndex
  }

}
