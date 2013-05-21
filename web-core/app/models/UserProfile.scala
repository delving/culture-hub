package models

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class UserProfile(isPublic: Boolean = false,
  fixedPhone: Option[String] = None,
  description: Option[String] = None,
  funFact: Option[String] = None,
  // place: EmbeddedLink, // TODO
  websites: List[String] = List.empty[String],
  twitter: Option[String] = None,
  facebook: Option[String] = None,
  linkedIn: Option[String] = None)