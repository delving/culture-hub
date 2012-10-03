package core

/**
 * The system fields used by the CultureHub
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class SystemField(name: String) {

  val tag = "delving_" + name
  val xmlKey = "delving:" + name

}

object SystemField {

  val values = List(TITLE, DESCRIPTION, PROVIDER, OWNER, THUMBNAIL, LANDING_PAGE, DEEP_ZOOM_URL, SPEC, COLLECTION)

  def valueOf(name: String) = values.find(v => v.name.toUpperCase == name.toUpperCase.replaceAll("_", "")).getOrElse(throw new IllegalArgumentException("Invalid SystemField " + name))

  def isValid(xmlKey: String) = values.exists(_.xmlKey == xmlKey)

  object TITLE extends SystemField("title")
  object DESCRIPTION extends SystemField("description")
  object THUMBNAIL extends SystemField("thumbnail")

  object CREATOR extends SystemField("creator")
  object LANDING_PAGE extends SystemField("landingPage")
  object DEEP_ZOOM_URL extends SystemField("deepZoomUrl")
  object OWNER extends SystemField("owner")  // dataProvider
  object PROVIDER extends SystemField("provider")

  object COLLECTION extends SystemField("collection")

  // TODO this is rather an Indexing Field
  object SPEC extends SystemField("spec")

}
