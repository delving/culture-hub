package models

import play.api.i18n.Messages

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DataSetState(name: String) {

  def description = Messages("dataSetState." + name.toLowerCase)
}

object DataSetState {
  val INCOMPLETE = DataSetState("incomplete")
  val PARSING = DataSetState("parsing")
  val UPLOADED = DataSetState("uploaded")
  val QUEUED = DataSetState("queued")
  val PROCESSING = DataSetState("processing")
  val ENABLED = DataSetState("enabled")
  val DISABLED = DataSetState("disabled")
  val ERROR = DataSetState("error")
  val NOTFOUND = DataSetState("notfound")
  def withName(name: String): Option[DataSetState] = if(valid(name)) Some(DataSetState(name)) else None
  def valid(name: String) = values.contains(DataSetState(name))
  val values = List(INCOMPLETE, PARSING, UPLOADED, QUEUED, PROCESSING, ENABLED, DISABLED, ERROR, NOTFOUND)
}
