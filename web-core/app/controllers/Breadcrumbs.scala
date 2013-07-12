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

      case "users" :: Nil => List(("/users", Messages("hubb.Users")))

      case "search" :: Nil => List(("NOLINK", Messages("hub.Search")))

      case "org" :: orgId :: "thing" :: spec :: recordId :: Nil =>
        val returnToResults = Option(p.get("search").get("returnToResults"))
        returnToResults match {
          case Some(r) if r.length() > 0 => List(("NOLINK", Messages("hub.Search")), ("/search?" + r, "%s".format(p.get("search").get("searchTerm"))), ("NOLINK", p.get("title").get("label")))
          case _ => List(("/admin", orgId), ("NOLINK", Messages("hubb.Objects")), ("NOLINK", spec), ("NOLINK", p.get("title").get("label")))
        }

      // TODO fetch these crumbs from the plugin

      case "org" :: orgId :: "museum" :: id :: Nil =>
        val returnToResults = Option(p.get("search").get("returnToResults"))
        returnToResults match {
          case Some(r) if r.length() > 0 => List(("NOLINK", Messages("hub.Search")), ("/search?" + r, "%s".format(p.get("search").get("searchTerm"))), ("NOLINK", p.get("title").get("label")))
          case _ => List(("/admin", orgId), ("NOLINK", Messages("musip.Museums")), ("NOLINK", p.get("title").get("label")))
        }

      case "org" :: orgId :: "collection" :: id :: Nil =>
        val returnToResults = Option(p.get("search").get("returnToResults"))
        returnToResults match {
          case Some(r) if r.length() > 0 => List(("NOLINK", Messages("hub.Search")), ("/search?" + r, "%s".format(p.get("search").get("searchTerm"))), ("NOLINK", p.get("title").get("label")))
          case _ => List(("/admin", orgId), ("NOLINK", Messages("musip.Collections")), ("NOLINK", p.get("title").get("label")))
        }

      case "rijks" :: "search" :: Nil => List(("/rijks", Messages("rijks.Rijkscollectie")), ("NOLINK", Messages("hub.Search")))

      case "slice" :: key :: "search" :: Nil => List(("/slice/" + key, Messages("namedslice.slice") + " " + key), ("NOLINK", Messages("hub.Search")))

      case "admin" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"))
      case "admin" :: "admin" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/admin", Messages("hub.OrganizationAdministration")))
      case "admin" :: "dataset" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/dataset", Messages("dataset.Datasets")))
      case "admin" :: "dataset" :: "add" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/dataset", Messages("dataset.CreateADataset")))
      case "admin" :: "dataset" :: name :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/dataset", Messages("dataset.Datasets")), ("/admin" + "/dataset" + name, name))
      case "admin" :: "dataset" :: name :: "update" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/dataset", Messages("dataset.Datasets")), ("/admin" + "/dataset/" + name, name), ("/admin" + "/dataset/" + name + "/update", Messages("hub.Edit")))
      case "admin" :: "groups" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/groups", Messages("hubb.Groups")))
      case "admin" :: "groups" :: "create" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/groups", Messages("hubb.Groups")), ("NOLINK", Messages("hub.Create")))
      case "admin" :: "groups" :: "update" :: id :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/groups", Messages("hubb.Groups")), ("/admin" + "/groups/update/" + id, Messages("hub.Edit")))
      case "admin" :: "sip-creator" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/sip-creator", Messages("hub.SIPCreator")))
      case "admin" :: "site" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/site", Messages("cms.WebsitePages")), ("NOLINK", Messages("locale." + request.session.get("lang").getOrElse(configuration.ui.defaultLanguage))))
      case "admin" :: "site" :: "upload" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/site", Messages("cms.WebsitePages")), ("NOLINK", Messages("cms.WebsitePages.upload")))
      case "admin" :: "site" :: lang :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/site", Messages("cms.WebsitePages")), ("NOLINK", Messages("locale." + lang)))
      case "admin" :: "site" :: lang :: "page" :: "add" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/site", Messages("cms.WebsitePages")), ("/admin" + "/site/" + lang, Messages("locale." + lang)), ("NOLINK", Messages("_cms.CreateNewPage")))
      case "admin" :: "site" :: lang :: "page" :: page :: "update" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/site", Messages("cms.WebsitePages")), ("/admin" + "/site/" + lang, Messages("locale." + lang)), ("NOLINK", Messages("cms.UpdatePage") + " \"" + page + "\""))
      case "admin" :: "virtualCollection" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/virtualCollection", Messages("chc.VirtualCollections")))
      case "admin" :: "virtualCollection" :: "add" :: Nil => List(("NOLINK", Messages("hubb.Organizations")), ("/admin", "Administration"), ("/admin" + "/virtualCollection", Messages("chc.VirtualCollections")), ("/admin" + "/virtualCollection/add", Messages("chc.NewVirtualCollection")))

      case user :: Nil => List(("/" + user, user))

      case _ => List()
    }
    (("/", Messages("hub.Home")) :: crumbs).zipWithIndex
  }

}