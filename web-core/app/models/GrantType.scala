package models

import play.api.Play
import play.api.Play.current

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

  def dynamicGrantTypes = Role.getAllRoles

  val cachedGrantTypes = (systemGrantTypes ++ dynamicGrantTypes.map(r => GrantType(r.key, r.description, "Config")))

  def allGrantTypes = if(Play.isDev) {
    (systemGrantTypes ++ dynamicGrantTypes.map(r => GrantType(r.key, r.description, "Config")))
  } else {
    cachedGrantTypes
  }

  def get(grantType: String) = allGrantTypes.find(_.key == grantType).getOrElse(illegal(grantType))

}