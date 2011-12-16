/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    val tokens = Link.find(query).map(l => Token(l._id, l.value("label"), Some(l.linkType))).toList
    Json(tokens)
  }

}