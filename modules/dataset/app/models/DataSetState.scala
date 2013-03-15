package models

import play.api.i18n.Messages

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DataSetState(name: String) {

  def description = Messages("dataSetState." + name.toLowerCase)

  override def toString: String = name.toUpperCase
}

object DataSetState {
  val INCOMPLETE = DataSetState("incomplete")
  val PARSING = DataSetState("parsing")
  val UPLOADED = DataSetState("uploaded")
  val QUEUED = DataSetState("queued")
  val PROCESSING_QUEUED = DataSetState("processingQueued")
  val PROCESSING = DataSetState("processing")
  val CANCELLED = DataSetState("cancelled")
  val ENABLED = DataSetState("enabled")
  val DISABLED = DataSetState("disabled")
  val ERROR = DataSetState("error")
  val NOTFOUND = DataSetState("notfound")
  def withName(name: String): Option[DataSetState] = if (valid(name)) Some(DataSetState(name)) else None
  def valid(name: String) = values.contains(DataSetState(name))
  val values = List(INCOMPLETE, PARSING, UPLOADED, QUEUED, PROCESSING_QUEUED, PROCESSING, CANCELLED, ENABLED, DISABLED, ERROR, NOTFOUND)
}
