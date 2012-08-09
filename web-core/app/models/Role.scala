package models

import play.api.Play.current
import play.api.i18n.{Messages, Lang}

/**
 * A role, granting rights to a resource. Each Role can also grant a set of resource rights regarding the resource (creation, modification, deletion)
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Role(key: String,
                description: Map[String, String],
                resourceRights: Seq[Role] = Seq.empty,
                origin: String = "System") {

  def getDescription(lang: Lang) = description.get(lang.language).getOrElse(description.values.head)
}

object Role {

  def illegal(key: String) = throw new IllegalArgumentException("Illegal key %s for Role".format(key))

  def description(key: String) = Lang.availables.map { lang =>
      (lang.language -> Messages("org.group.grantType." + key))
  }.toMap

  val VIEW = Role("view", description("view"), Seq(MetaRole.CAN_VIEW))
  val MODIFY = Role("modify", description("modify"), Seq(MetaRole.CAN_VIEW, MetaRole.CAN_UPDATE))
  val OWN = Role("own", description("own"), Seq(MetaRole.CAN_VIEW, MetaRole.CAN_CREATE, MetaRole.CAN_UPDATE, MetaRole.CAN_DELETE))

  // TODO move to plugin
  val CMS = Role("cms", description("cms"))

  val systemGrantTypes = List(VIEW, MODIFY, CMS, OWN)

  def dynamicGrantTypes(configuration: DomainConfiguration) = configuration.roles

  def computeGrantTypes(configuration: DomainConfiguration) = (systemGrantTypes ++ dynamicGrantTypes(configuration))

  def allGrantTypes(configuration: DomainConfiguration) = computeGrantTypes(configuration)

  def get(grantType: String)(implicit configuration: DomainConfiguration) = allGrantTypes(configuration).find(_.key == grantType).getOrElse(illegal(grantType))

}

object MetaRole {

  // TODO i18n
  val CAN_VIEW = Role("canView", Map("en" -> "Can view"))
  val CAN_CREATE = Role("canCreate", Map("en" -> "Can create"))
  val CAN_UPDATE = Role("canUpdate", Map("en" -> "Can update"))
  val CAN_DELETE = Role("canDelete", Map("en" -> "Can delete"))

}