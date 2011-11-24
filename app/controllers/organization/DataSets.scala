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

  def list(orgId: String): Result = {
    val dataSetsPage = DataSet.findAllCanSee(orgId, connectedUser)
    val items: List[ShortDataSet] = dataSetsPage
    Template('title -> listPageTitle("dataset"), 'items -> items.sortBy(_.spec), 'count -> dataSetsPage.size, 'isOwner -> Organization.isOwner(orgId, connectedUser))
  }

  def dataSet(orgId: String, spec: String): Result = {
    val dataSet = DataSet.findBySpecAndOrgId(spec, orgId)
    val ds = dataSet.getOrElse(return NotFound(&("organization.datasets.dataSetNotFound", spec)))
    if(!DataSet.canView(ds, connectedUser)) return NotFound(&("datasets.dataSetNotFound", ds.spec))
    val describedFacts = DataSet.factDefinitionList.map(factDef => Fact(factDef.name, factDef.prompt, Option(ds.details.facts.get(factDef.name)).getOrElse("").toString))
    Template('dataSet -> ds, 'canEdit -> DataSet.canEdit(ds, connectedUser), 'facts -> asJavaList(describedFacts), 'isOwner -> Organization.isOwner(orgId, connectedUser))
  }

  def listAsTokens(orgId: String, q: String): Result = {
    val dataSets = DataSet.find(MongoDBObject("orgId" -> orgId, "deleted" -> false, "spec" -> Pattern.compile(q, Pattern.CASE_INSENSITIVE)))
    val asTokens = dataSets.map(ds => Token(ds._id, ds.spec))
    Json(asTokens)
  }


}