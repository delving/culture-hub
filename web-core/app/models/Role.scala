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

  val OWN = Role("own", description("own"), Seq(ResourceRole.CAN_VIEW, ResourceRole.CAN_CREATE, ResourceRole.CAN_UPDATE, ResourceRole.CAN_DELETE))

  // TODO move to plugin
  val CMS = Role("cms", description("cms"), Seq.empty, None)

  val systemGrantTypes = List(CMS, OWN)

  def dynamicGrantTypes(configuration: DomainConfiguration) = configuration.roles

  def computeGrantTypes(configuration: DomainConfiguration) = (systemGrantTypes ++ dynamicGrantTypes(configuration))

  def allGrantTypes(configuration: DomainConfiguration): Seq[Role] = computeGrantTypes(configuration)

  def get(grantType: String)(implicit configuration: DomainConfiguration) = allGrantTypes(configuration).find(_.key == grantType).getOrElse(illegal(grantType))

}

object ResourceRole {

  val RESOURCE_TYPE_SYSTEM = Some(ResourceType("system"))

  // TODO i18n
  val CAN_VIEW = Role("canView", Map("en" -> "Can view"), Seq.empty, RESOURCE_TYPE_SYSTEM)
  val CAN_CREATE = Role("canCreate", Map("en" -> "Can create"), Seq.empty, RESOURCE_TYPE_SYSTEM)
  val CAN_UPDATE = Role("canUpdate", Map("en" -> "Can update"), Seq.empty, RESOURCE_TYPE_SYSTEM)
  val CAN_DELETE = Role("canDelete", Map("en" -> "Can delete"), Seq.empty, RESOURCE_TYPE_SYSTEM)

}