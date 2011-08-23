package controllers

import play.mvc.results.Result
import models.{DataSet}

/**
 * This controller holds logic for repositories (all sorts of collections)
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Repositories extends DelvingController {

  /** list repositories of all kind (ingested collections, user collections, ...) as tokens, for the connected user **/
  def listWriteableAsTokens: Result = {
    // TODO also list shared collections, not only owned ones
    val repos = for(dataSet <- DataSet.findAllByOwner(getUserReference)) yield Token(dataSet._id.toString, dataSet.name)
    Json(repos)
  }

}