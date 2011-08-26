package controllers

import play.templates.Html
import models.DataSet

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends DelvingController {

  import views.Dataset._

  def list: Html = {
    // TODO visibility (public, private)
    val dataSets = DataSet.findAllByOwner(connectedUserId)
    html.list(dataSets)
  }

  def view(spec: String): Html = {
    // TODO check if connected user has access
    val dataSet = DataSet.findBySpec(spec)
    html.view(dataSet)
  }

}