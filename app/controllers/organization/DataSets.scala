package controllers.organization

import play.api.mvc.Action
import models.DataSet
import collection.JavaConverters._
import play.api.i18n.Messages
import com.mongodb.casbah.commons.MongoDBObject
import controllers.{Token, Fact, ShortDataSet, OrganizationController}
import java.util.regex.Pattern

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends OrganizationController {

  def list(orgId: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val dataSetsPage = DataSet.findAllCanSee(orgId, userName)
        val items: List[ShortDataSet] = dataSetsPage
        Ok(Template('title -> listPageTitle("dataset"), 'items -> items.sortBy(_.spec), 'count -> dataSetsPage.size))
    }
  }

  def dataSet(orgId: String, spec: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val maybeDataSet = DataSet.findBySpecAndOrgId(spec, orgId)
        if (maybeDataSet.isEmpty) {
          NotFound(Messages("organization.datasets.dataSetNotFound", spec))
        } else {
          val ds = maybeDataSet.get
          if (!DataSet.canView(ds, userName)) {
            NotFound(Messages("datasets.dataSetNotFound", ds.spec))
          } else {
            val describedFacts = DataSet.factDefinitionList.map(factDef => Fact(factDef.name, factDef.prompt, Option(ds.details.facts.get(factDef.name)).getOrElse("").toString))
            Ok(Template('dataSet -> ds, 'canEdit -> DataSet.canEdit(ds, userName), 'facts -> describedFacts.asJava))
          }
        }
    }
  }

  def listAsTokens(orgId: String, q: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val dataSets = DataSet.find(MongoDBObject("orgId" -> orgId, "deleted" -> false, "spec" -> Pattern.compile(q, Pattern.CASE_INSENSITIVE)))
        val asTokens = dataSets.map(ds => Token(ds._id, ds.spec, Some("dataset")))
        Json(asTokens)
    }
  }

}

