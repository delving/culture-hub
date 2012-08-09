package models

import core.access.ResourceType
import play.api.Play.current
import play.api.i18n.{Messages, Lang}

/**
 * A role, granting rights to a resource.
 * Each Role can also grant a set of resource rights (e.g. creation, modification, deletion) to a resource
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Role(key: String,
                description: Map[String, String],
                resourceRights: Seq[Role] = Seq.empty,
                resourceType: Option[ResourceType] = None) {

  def getDescription(lang: Lang) = description.get(lang.language).getOrElse(description.values.head)
}

object Role {

  def illegal(key: String) = throw new IllegalArgumentException("Illegal key %s for Role".format(key))

  def description(key: String) = Lang.availables.map { lang =>
      (lang.language -> Messages("org.group.grantType." + key))
  }.toMap

  // TODO move to plugin, or better yet, replace by the generic resource rights
  val VIEW = Role("view", description("view"), Seq(MetaRole.CAN_VIEW), Some(DataSet.RESOURCE_TYPE))
  val MODIFY = Role("modify", description("modify"), Seq(MetaRole.CAN_VIEW, MetaRole.CAN_UPDATE), Some(DataSet.RESOURCE_TYPE))
  val OWN = Role("own", description("own"), Seq(MetaRole.CAN_VIEW, MetaRole.CAN_CREATE, MetaRole.CAN_UPDATE, MetaRole.CAN_DELETE), Some(DataSet.RESOURCE_TYPE))

  // TODO move to plugin
  val CMS = Role("cms", description("cms"), Seq.empty, None)

  val systemGrantTypes = List(VIEW, MODIFY, CMS, OWN)

  def dynamicGrantTypes(configuration: DomainConfiguration) = configuration.roles

  def computeGrantTypes(configuration: DomainConfiguration) = (systemGrantTypes ++ dynamicGrantTypes(configuration))

  def allGrantTypes(configuration: DomainConfiguration) = computeGrantTypes(configuration)

  def get(grantType: String)(implicit configuration: DomainConfiguration) = allGrantTypes(configuration).find(_.key == grantType).getOrElse(illegal(grantType))

}

// TODO remember what we designed this for.
object MetaRole {

  val RESOURCE_TYPE_SYSTEM = Some(ResourceType("system"))

  // TODO i18n
  val CAN_VIEW = Role("canView", Map("en" -> "Can view"), Seq.empty, RESOURCE_TYPE_SYSTEM)
  val CAN_CREATE = Role("canCreate", Map("en" -> "Can create"), Seq.empty, RESOURCE_TYPE_SYSTEM)
  val CAN_UPDATE = Role("canUpdate", Map("en" -> "Can update"), Seq.empty, RESOURCE_TYPE_SYSTEM)
  val CAN_DELETE = Role("canDelete", Map("en" -> "Can delete"), Seq.empty, RESOURCE_TYPE_SYSTEM)

}