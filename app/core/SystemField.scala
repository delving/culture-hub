package core

/**
 * The system fields used by the CultureHub
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class SystemField(name: String) {

  val tag = "delving_" + name

}

object SystemField {

  val values = List(TITLE, DESCRIPTION, OWNER, CREATOR, THUMBNAIL, LANDING_PAGE, DEEP_ZOOM_URL, PROVIDER, SPEC)

  def valueOf(name: String) = values.find(v => v.name.toUpperCase == name.toUpperCase.replaceAll("_", "")).getOrElse(throw new IllegalArgumentException("Invalid SystemField " + name))

  object TITLE extends SystemField("title")
  object DESCRIPTION extends SystemField("description")
  object OWNER extends SystemField("owner")
  object CREATOR extends SystemField("create")
  object THUMBNAIL extends SystemField("thumbnail")
  object LANDING_PAGE extends SystemField("landingPage")
  object DEEP_ZOOM_URL extends SystemField("deepZoomUrl")
  object PROVIDER extends SystemField("provider")
  object SPEC extends SystemField("spec")

}
