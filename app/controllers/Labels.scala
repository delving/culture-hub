package controllers

import play.mvc.results.Result
import com.mongodb.casbah.commons.MongoDBObject
import java.util.regex.Pattern
import models.Link

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Labels extends DelvingController {

  def listAsTokens(q: String): Result = {
    val query = MongoDBObject("value.label" -> Pattern.compile(q, Pattern.CASE_INSENSITIVE), "userName" -> connectedUser)
    val tokens = Link.find(query).map(l => Token(l._id, l.value.label)).toList
    Json(tokens)
  }

}