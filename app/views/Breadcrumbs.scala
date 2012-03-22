package views

import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import models.{PortalTheme, UserCollection, DObject}


/**
 * Breadcrumb computation based on URL. Context data is passed in through a map of maps, the inner map containing (url, label)
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Breadcrumbs {
  
  def crumble(p: java.util.Map[String, java.util.Map[String, String]], request: RequestHeader, theme: PortalTheme): List[((String, String), Int)] = {

    // we can't make the difference between orgId/object and user/object
    val crumbList = if(p != null && p.containsKey(controllers.Search.IN_ORGANIZATION)) {
      "org" :: request.path.split("/").drop(1).toList
    } else {
      request.path.split("/").drop(1).toList
    }

    val crumbs = crumbList match {

      case "users" :: Nil => List(("/users", Messages("thing.users")))
      case "objects" :: Nil => List(("/objects", Messages("thing.objects.user")))
      case "heritageObjects" :: Nil => List(("/objects", Messages("thing.objects.heritage")))
      case "collections" :: Nil => List(("/collections", Messages("thing.collection")))
      case "stories" :: Nil => List(("/stories", Messages("thing.stories")))

      case "search" :: Nil if p != null =>
        val returnToResults = p.get("search").get("returnToResults")
        val searchTerm = "[%s]".format(p.get("search").get("searchTerm"))
        List(("NOLINK", Messages("ui.label.search")), ("/search?" + returnToResults , searchTerm))

      case "org" :: orgId :: "object" :: spec :: recordId :: Nil =>
        val returnToResults = Option(p.get("search").get("returnToResults"))
        returnToResults match {
          case Some(r) if r.length() > 0 => List(("NOLINK", Messages("ui.label.search")), ("/search?" + r, "[%s]".format(p.get("search").get("searchTerm"))), ("NOLINK", p.get("title").get("label")))
          case _ => List(("/organizations/" + orgId, orgId), ("NOLINK", Messages("thing.objects")), ("NOLINK", spec), ("NOLINK", p.get("title").get("label")))
        }

      case "organizations" :: orgName :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName))
      case "organizations" :: orgName :: "admin" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/admin", Messages("org.admin.index.title")))
      case "organizations" :: orgName :: "dataset" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/dataset", Messages("thing.datasets")))
      case "organizations" :: orgName :: "dataset" :: name :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/dataset", Messages("thing.datasets")), ("/organizations/" + orgName + "/dataset" + name, name))
      case "organizations" :: orgName :: "dataset" :: name :: "update" ::  Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/dataset", Messages("thing.datasets")), ("/organizations/" + orgName + "/dataset/" + name, name), ("/organizations/" + orgName + "/dataset/" + name + "/update", Messages("ui.label.edit")))
      case "organizations" :: orgName :: "groups" ::  Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/groups", Messages("thing.groups")))
      case "organizations" :: orgName :: "groups" ::  "create" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/groups", Messages("thing.groups")), ("NOLINK", Messages("ui.label.create")))
      case "organizations" :: orgName :: "groups" ::  "update" :: id :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/groups", Messages("thing.groups")), ("/organizations/" + orgName + "/groups/update/" + id, Messages("ui.label.edit")))
      case "organizations" :: orgName :: "sip-creator" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/sip-creator", Messages("ui.label.sipcreator")))
      case "organizations" :: orgName :: "site" :: Nil =>  List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", Messages("org.cms")), ("NOLINK", Messages("locale." + request.session.get("lang").getOrElse(theme.defaultLanguage))))
      case "organizations" :: orgName :: "site" :: "upload" :: Nil =>  List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", Messages("org.cms")), ("NOLINK", Messages("org.cms.upload")))
      case "organizations" :: orgName :: "site" :: lang :: Nil =>  List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", Messages("org.cms")), ("NOLINK", Messages("locale." + lang)))
      case "organizations" :: orgName :: "site" :: lang :: "page" :: "add" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", Messages("org.cms")), ("/organizations/" + orgName + "/site/" + lang, Messages("locale." + lang)), ("NOLINK", Messages("org.cms.page.create")))
      case "organizations" :: orgName :: "site" :: lang :: "page" :: page :: "update" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/site", Messages("org.cms")), ("/organizations/" + orgName + "/site/" + lang, Messages("locale." + lang)), ("NOLINK", Messages("org.cms.page.update") + " \"" + page + "\""))
      case "organizations" :: orgName :: "virtualCollection" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/virtualCollection", Messages("thing.virtualCollections")))
      case "organizations" :: orgName :: "virtualCollection" :: "add" :: Nil => List(("NOLINK", Messages("thing.organizations")), ("/organizations/" + orgName, orgName), ("/organizations/" + orgName + "/virtualCollection", Messages("thing.virtualCollections")), ("/organizations/" + orgName + "/virtualCollection/add", Messages("organization.virtualCollection.new")))


      case user :: Nil => List(("/" + user, user))
      case user :: "collection" :: Nil => List(("/" + user, user), ("/" + user + "/collection", Messages("thing.collections")))
      case user :: "object" :: Nil => List(("/" + user, user), ("/" + user + "/object", Messages("thing.objects")))
      case user :: "dataset" :: Nil => List(("/" + user, user), ("/" + user + "/dataset", Messages("thing.datasets")))
      case user :: "story" :: Nil => List(("/" + user, user), ("/" + user + "/story", Messages("thing.stories")))

      case user :: "object" :: "add" :: Nil => List(("/" + user, user), ("/" + user + "/object", Messages("thing.objects")), ("/" + user + "/object/add", Messages("user.object.create")))
      case user :: "collection" :: "add" :: Nil => List(("/" + user, user), ("/" + user + "/collection", Messages("thing.collections")), ("/" + user + "/collection/add", Messages("user.collection.create")))
      case user :: "story" :: "add" :: Nil => List(("/" + user, user), ("/" + user + "/story", Messages("thing.stories")), ("/" + user + "/story/add", Messages("user.story.create")))

      case user :: "object" :: id :: Nil => List(("/" + user, user), ("/" + user + "/object", Messages("thing.objects")), ("/" + user + "/object/" + id, DObject.fetchName(id)))
      case user :: "collection" :: id :: Nil => List(("/" + user, user), ("/" + user + "/collection", Messages("thing.collections")), ("/" + user + "/collection/" + id, UserCollection.fetchName(id)))
      case user :: "story" :: id :: Nil => List(("/" + user, user), ("/" + user + "/story", Messages("thing.stories")), ("/" + user + "/story/" + id, models.Story.fetchName(id)))
      case user :: "story" :: id :: "read" :: Nil => List(("/" + user, user), ("/" + user + "/story", Messages("thing.stories")), ("/" + user + "/story/" + id, models.Story.fetchName(id)), ("/" + user + "/story/" + id, Messages("thing.story")))

      case user :: "object" :: id :: "update" :: Nil => List(("/" + user, user), ("/" + user + "/object", Messages("thing.objects")), ("/" + user + "/object/" + id,  DObject.fetchName(id)),("/" + user + "/object/" + id, Messages("user.object.updateObject", DObject.fetchName(id))))
      case user :: "collection" :: id :: "update" :: Nil => List(("/" + user, user), ("/" + user + "/collection", Messages("thing.collections")), ("/" + user + "/collection/" + id,  UserCollection.fetchName(id)),("/" + user + "/collection/" + id, Messages("user.collection.update", UserCollection.fetchName(id))))
      case user :: "story" :: id :: "update" :: Nil => List(("/" + user, user), ("/" + user + "/story", Messages("thing.stories")),("/" + user + "/story/" + id,  models.Story.fetchName(id)),("/" + user + "/story/" + id, Messages("user.story.updateStory", models.Story.fetchName(id))))

      case user :: "collection" :: cid :: "object" :: oid ::Nil => List(("/" + user, user), ("/" + user + "/collection", Messages("thing.collections")), ("/" + user + "/collection/" + cid, UserCollection.fetchName(cid)), ("/" + user + "/collection/" + cid + "/object/" + oid, DObject.fetchName(oid)))

      case _ => List()
    }
    (("/", "Home") :: crumbs).zipWithIndex
  }

}
