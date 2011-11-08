package controllers.organization

import java.util.regex.Pattern
import play.mvc.results.Result
import com.mongodb.casbah.commons.MongoDBObject
import collection.JavaConversions._
import controllers.{Fact, ShortDataSet, Token, DelvingController}
import models.{Organization, DataSet}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends DelvingController with OrganizationSecured {

  def list(orgId: String, page: Int = 1): Result = {
    val dataSetsPage = DataSet.findAllByOrgId(orgId).page(page)
    val items: List[ShortDataSet] = dataSetsPage._1
    Template('title -> listPageTitle("dataset"), 'items -> items, 'page -> page, 'count -> dataSetsPage._2, 'isOwner -> Organization.isOwner(connectedUser))
  }

  def dataSet(orgId: String, spec: String): Result = {
    // TODO check if connected user has access
    val dataSet = DataSet.findBySpecAndOrgId(spec, orgId)

    request.format match {
      case "html" => {
        val ds = dataSet.getOrElse(return NotFound(&("organization.datasets.dataSetNotFound", spec)))
        val describedFacts = DataSet.factDefinitionList.map(factDef => Fact(factDef.name, factDef.prompt, Option(ds.details.facts.get(factDef.name)).getOrElse("").toString))
        Template('dataSet -> ds, 'facts -> asJavaList(describedFacts))
      }
      case "json" => if (dataSet == None) Json(ShortDataSet(userName = connectedUser, orgId = orgId))
      else {
        val dS = dataSet.get
        Json(ShortDataSet(id = Some(dS._id), spec = dS.spec, facts = dS.getFacts, userName = dS.getCreator.userName, orgId = dS.orgId, recordDefinitions = dS.recordDefinitions))
      }
    }

  }

  def listAsTokens(orgId: String, q: String): Result = {
    val dataSets = DataSet.find(MongoDBObject("orgId" -> orgId, "deleted" -> false, "spec" -> Pattern.compile(q, Pattern.CASE_INSENSITIVE)))
    val asTokens = dataSets.map(ds => Token(ds._id, ds.spec))
    Json(asTokens)
  }


}