package models

/**
 * A user Role, with internationalized descriptions
 *
 * TODO merge with GrantType
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Role(key: String, description: Map[String, String])