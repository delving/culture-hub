package controllers.organization

import controllers.DelvingController
import java.util.regex.Pattern
import play.mvc.results.Result
import com.mongodb.casbah.commons.MongoDBObject
import models.DataSet

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends DelvingController with OrganizationSecured {

  def listDataSetsAsTokens(q: String): Result = {
    val dataSets = DataSet.find(MongoDBObject("orgId" -> Pattern.compile(q, Pattern.CASE_INSENSITIVE)))
    val asTokens = dataSets.map(ds => Map("id" -> ds._id, "name" -> ds.spec))
    Json(asTokens)
  }


}