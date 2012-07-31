package models

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class GrantType(key: String, description: String, origin: String = "System")

object GrantType {

  def illegal(key: String) = throw new IllegalArgumentException("Illegal key %s for GrantType".format(key))

  def description(key: String) = play.api.i18n.Messages("org.group.grantType." + key)

  val VIEW = GrantType("view", description("view"))
  val MODIFY = GrantType("modify", description("modify"))
  val CMS = GrantType("cms", description("cms"))
  val OWN = GrantType("own", description("own"))

  val systemGrantTypes = List(VIEW, MODIFY, CMS, OWN)

  def dynamicGrantTypes(configuration: DomainConfiguration) = configuration.roles

  def computeGrantTypes(configuration: DomainConfiguration) = (systemGrantTypes ++ dynamicGrantTypes(configuration).map(r => GrantType(r.key, r.description("en"), "Config")))

  def allGrantTypes(configuration: DomainConfiguration) = computeGrantTypes(configuration)

  def get(grantType: String)(implicit configuration: DomainConfiguration) = allGrantTypes(configuration).find(_.key == grantType).getOrElse(illegal(grantType))

}