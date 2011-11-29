package controllers.ws

import controllers.DelvingController
import play.mvc.results.Result
import play.libs.WS
import scala.collection.JavaConversions._
import com.mongodb.casbah.Imports._
import models.salatContext._
import java.util.regex.Pattern

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Places extends DelvingController {

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