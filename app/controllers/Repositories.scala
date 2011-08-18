package controllers

import play.mvc.results.Result
import models.DataSet

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Repositories extends DelvingController {

  /** list repositories of all kind as tokens **/
  def listAsTokens: Result = {
    val repos = for(dataSet <- DataSet.findAllByOwner(getUserReference)) yield Token(dataSet._id.toString, dataSet.name)
    Json(repos)
  }

}