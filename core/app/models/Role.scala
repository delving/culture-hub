package models

import play.api.Play
import play.api.Play.current

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Role(key: String, description: String)

object Role {

  def getAllRoles = Play.configuration.getConfig("roles").map {
    rolesConfig => rolesConfig.keys.map {
      r =>
        Role(r, rolesConfig.getString(r).getOrElse(""))
    }
  }.getOrElse {
    Seq.empty
  }.toSeq

}
