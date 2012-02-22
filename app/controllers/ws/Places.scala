package controllers.ws

import play.api.mvc._
import controllers.{DelvingController, Secured}
import models.mongoContext._
import com.mongodb.casbah.Imports._
import java.util.regex.Pattern

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Places extends DelvingController with Secured {

  def find = Root {
    Action {
      implicit request =>
        val fields = List("countryName", "adminCode1", "fclName", "countryCode", "lng", "fcodeName", "toponymName", "fcl", "name", "fcode", "geonameID", "lat", "adminName1", "population")

        val filtered = geonamesCollection.find(MongoDBObject(
          "name" -> Pattern.compile(Pattern.quote(request.queryString.get("q").getOrElse(Seq())(0)), Pattern.CASE_INSENSITIVE),
          "continentCode" -> "EU",
          "fcl" -> "P"
        ), fields.map(f => (f, 1)).toMap).map(result => {
          result.put("id", result.get("geonameID"))
          result
        })

        Json(filtered)

    }
  }

}
