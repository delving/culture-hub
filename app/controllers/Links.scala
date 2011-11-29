package controllers

import play.mvc.results.Result
import com.mongodb.casbah.commons.MongoDBObject
import java.util.regex.Pattern
import models.Link

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Links extends DelvingController {

  def listFreeTextAsTokens(q: String): Result = {
    val query = MongoDBObject("value.label" -> Pattern.compile(Pattern.quote(q), Pattern.CASE_INSENSITIVE), "userName" -> connectedUser, "linkType" -> Link.LinkType.FREETEXT)
    val tokens = Link.find(query).map(l => Token(l._id, l.value("label"), l.linkType)).toList
    Json(tokens)
  }

}