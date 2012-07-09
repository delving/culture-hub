package controllers.organization

import play.api.mvc.{WebSocket, Action}
import models.DataSet
import collection.JavaConverters._
import play.api.i18n.Messages
import com.mongodb.casbah.commons.MongoDBObject
import controllers.{Token, Fact, ShortDataSet, OrganizationController}
import java.util.regex.Pattern
import play.api.libs.json.{JsString, JsValue}
import core.DataSetEventFeed
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.{Enumerator, Done, Input, Iteratee}
import util.ThemeHandler

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends OrganizationController {

  def list(orgId: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        Ok(Template('title -> listPageTitle("dataset")))
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

  def feed(orgId: String, clientId: String, spec: Option[String]) = WebSocket.async[JsValue] { implicit request  =>
    if(request.session.get("userName").isDefined) {
      val portalTheme = ThemeHandler.getByDomain(request.domain)
      DataSetEventFeed.subscribe(orgId, clientId, session.get("userName").get, portalTheme.name, spec)
    } else {
      // return a fake pair
      // TODO perhaps a better way here ?
      Promise.pure((Done[JsValue, JsValue](JsString(""), Input.Empty), Enumerator.imperative()))
    }
  }

  // TODO[manu] deprecate this one (used by groups, needs data migration)
  def listAsTokens(orgId: String, q: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val dataSets = DataSet.find(MongoDBObject("orgId" -> orgId, "deleted" -> false, "spec" -> Pattern.compile(q, Pattern.CASE_INSENSITIVE)))
        val asTokens = dataSets.map(ds => Token(ds._id, ds.spec, Some("dataset")))
        Json(asTokens)
    }
  }

  def listAsTokensBySpec(orgId: String, q: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val dataSets = DataSet.find(MongoDBObject("orgId" -> orgId, "deleted" -> false, "spec" -> Pattern.compile(q, Pattern.CASE_INSENSITIVE)))
        val asTokens = dataSets.map(ds => Token(ds.spec, ds.spec, Some("dataset")))
        Json(asTokens)
    }
  }

}

