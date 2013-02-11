package core

import core.indexing.IndexableField

/**
 * The system fields used by the CultureHub.
 *
 * Don't touch this class unless there has been a consensus amongst the core developers.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class SystemField(name: String) extends IndexableField {

  val tag = "delving_" + name
  lazy val key = tag
  val xmlKey = "delving:" + name

}

object SystemField {

  val values = List(TITLE, DESCRIPTION, PROVIDER, OWNER, THUMBNAIL, IMAGE_URL, LANDING_PAGE, DEEP_ZOOM_URL, SPEC, COLLECTION)

  def valueOf(name: String) = values.find(v => v.name.toUpperCase == name.toUpperCase.replaceAll("_", "")).getOrElse(throw new IllegalArgumentException("Invalid SystemField " + name))

  def isValid(tag: String) = values.exists(_.tag == tag)

  object TITLE extends SystemField("title")
  object DESCRIPTION extends SystemField("description")
  object THUMBNAIL extends SystemField("thumbnail")
  object IMAGE_URL extends SystemField("imageUrl")

  object CREATOR extends SystemField("creator")
  object LANDING_PAGE extends SystemField("landingPage")
  object DEEP_ZOOM_URL extends SystemField("deepZoomUrl")
  object OWNER extends SystemField("owner") // dataProvider
  object PROVIDER extends SystemField("provider")

  object COLLECTION extends SystemField("collection")

  // TODO this is rather an Indexing Field
  object SPEC extends SystemField("spec")

}
