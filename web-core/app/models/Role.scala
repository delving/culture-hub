package models

import core.access.ResourceType
import play.api.Play.current
import play.api.i18n.{Messages, Lang}

/**
 * A role, granting rights to a resource.
 *
 * Each Role can also grant a set of resource rights (e.g. creation, modification, deletion) to a resource.
 *
 * Roles can also grant the privileges to aggregated "unit" roles. Unit roles are not directly visible to the user,
 * but can be defined by the system to be used by super-roles.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Role(key: String,
                description: Map[String, String],
                isResourceAdmin: Boolean = false,
                resourceType: Option[ResourceType] = None,
                isUnitRole: Boolean = false,
                unitRoles: Seq[Role] = Seq.empty
               ) {

  def getDescription(lang: Lang) = {
    if (description.isEmpty) println(key)
    description.get(lang.language).getOrElse(description.values.headOption.getOrElse(""))
  }

  override def equals(r: Any): Boolean = r.isInstanceOf[Role] && r.asInstanceOf[Role].key == key
}

object Role {

  def illegal(key: String) = throw new IllegalArgumentException("Illegal key %s for Role".format(key))

  def descriptions(key: String) = Lang.availables.map { lang =>
      (lang.language -> Messages(key))
  }.toMap

  val OWN = Role("own", descriptions("org.group.grantType.own"), false)

  val systemRoles = List(OWN)

  def dynamicRoles(configuration: DomainConfiguration) = configuration.roles

  def computeRoles(configuration: DomainConfiguration) = (systemRoles ++ dynamicRoles(configuration))

  def allRoles(configuration: DomainConfiguration): Seq[Role] = computeRoles(configuration)

  def allPrimaryRoles(configuration: DomainConfiguration): Seq[Role] = allRoles(configuration).filterNot(_.isUnitRole)

  def get(role: String)(implicit configuration: DomainConfiguration) = allRoles(configuration).find(_.key == role).getOrElse(illegal(role))

}