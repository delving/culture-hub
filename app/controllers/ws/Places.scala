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

package controllers.ws

import play.mvc.results.Result
import scala.collection.JavaConversions._
import com.mongodb.casbah.Imports._
import models.salatContext._
import java.util.regex.Pattern
import controllers.{Secure, DelvingController}

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Places extends DelvingController with Secure {

  def find(q: String): Result = {

    val fields = List("countryName", "adminCode1", "fclName", "countryCode", "lng", "fcodeName", "toponymName", "fcl", "name", "fcode", "geonameID", "lat", "adminName1", "population")
//    val fields = List("countryName", "lng", "name", "geonameID", "lat")

    val filtered = geonamesCollection.find(MongoDBObject(
      "name" -> Pattern.compile(Pattern.quote(q), Pattern.CASE_INSENSITIVE),
      "continentCode" -> "EU",
//      "lang" -> theme.defaultLanguage,
      "fcl" -> "P"
    ), fields.map(f => (f, 1)).toMap).map(result => {
        result.put("id", result.get("geonameID"))
        result
    })

    Json(filtered)
  }

}