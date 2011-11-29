package controllers.ws

import controllers.DelvingController
import play.mvc.results.Result
import play.libs.WS
import scala.collection.JavaConversions._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Places extends DelvingController {

  def find(q: String): Result = {

    // for now, got to GeoNames directly and fetch stuff from there.
    // later we instead should go to our own thing and use either a dump or something else.

    val ws = WS.url("http://api.geonames.org/searchJSON").params(Map(
      "name_startsWith" -> q,
      "continentCode" -> "EU",
      "type" -> "json",
      "username" -> "manuelbernhardt",
      "lang" -> theme.defaultLanguage,
      "featureClass" -> "P" // political entities
    )).get()

    val fields = List("countryName", "adminCode1", "fclName", "countryCode", "lng", "fcodeName", "toponymName", "fcl", "name", "fcode", "geonameId", "lat", "adminName1", "population")
    val filtered = ws.getJson.getAsJsonObject.get("geonames").getAsJsonArray.map(_.getAsJsonObject).map(g => (fields.map(f => (f, g.get(f).getAsString)).toMap) + ("id" -> -1)).toList
    Json(filtered)
  }

}