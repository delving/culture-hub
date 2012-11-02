package controllers.ws

import play.api.mvc._
import controllers.{Token, DelvingController, Secured}
import models.HubMongoContext._
import com.mongodb.casbah.Imports._
import java.util.regex.Pattern

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Places extends DelvingController with Secured {

  def listAsTokens(q: String, countryCode: Option[String]) = Root {
    Action {
      implicit request =>
        val places = query(q, countryCode)
        val asTokens = places.map(p => Token(p.get("name").toString, p.get("name").toString))
        Json(asTokens)
    }
  }

  private def query(q: String, countryCode: Option[String]): Seq[DBObject] = {
    val fields = List("countryName", "adminCode1", "fclName", "countryCode", "lng", "fcodeName", "toponymName", "fcl", "name", "fcode", "geonameID", "lat", "adminName1", "population")

    val query = MongoDBObject(
      "name" -> Pattern.compile(Pattern.quote(q), Pattern.CASE_INSENSITIVE),
      "continentCode" -> "EU",
      "fcl" -> "P"
    )

    val filteredQuery = countryCode.map { cc =>
      query ++ MongoDBObject("countryCode" -> cc)
    }.getOrElse {
      query
    }

    geonamesCollection.find(filteredQuery, fields.map(f => (f, 1)).toMap).
      map(result => {
        result.put("id", result.get("geonameID"))
        result
      }).toSeq
  }


}
