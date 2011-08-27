package controllers.user

import play.mvc.results.Result
import extensions.CHJson
import models.DataSet
import scala.collection.JavaConversions._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.{BasicDBObject, WriteConcern}
import controllers.{Secure, UserAuthentication, DelvingController}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends DelvingController with UserSecured {

  import views.User.Dataset._

  def factsUpdate(spec: String): AnyRef = if(spec.isEmpty) BadRequest else html.facts(spec, controllers.DataSets.factDefinitionList)

  def factsSubmit(data: String): Result = {
    val facts = CHJson.parse[Map[String, String]](data)
    val spec: String = facts("spec")
    val factsObject = new BasicDBObject(facts)
    DataSet.update(MongoDBObject("spec" -> spec), MongoDBObject("$set" -> MongoDBObject("details.facts" -> factsObject)), false, false, new WriteConcern())
    Ok
  }
}

