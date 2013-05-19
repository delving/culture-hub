package controllers.organization

import play.api.mvc.{ WebSocket, Action }
import models.DataSet
import play.api.i18n.Messages
import com.mongodb.casbah.Imports._
import controllers.{ Token, OrganizationController }
import java.util.regex.Pattern
import play.api.libs.json.{ JsString, JsValue }
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.{ Concurrent, Enumerator, Done, Input }
import util.OrganizationConfigurationHandler

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends OrganizationController {

  def list(orgId: String) = OrganizationMember {
    Action {
      implicit request =>
        Ok(Template('title -> listPageTitle("dataset"), 'canAdministrate -> DataSet.dao.canAdministrate(connectedUser)))
    }
  }

  def dataSet(orgId: String, spec: String) = OrganizationMember {
    Action {
      implicit request =>
        val maybeDataSet = DataSet.dao.findBySpecAndOrgId(spec, orgId)
        if (maybeDataSet.isEmpty) {
          NotFound(Messages("_dataset.DatasetWasNotFound", spec))
        } else {
          val ds = maybeDataSet.get
          Ok(Template('spec -> ds.spec))
        }
    }
  }

  def feed(orgId: String, clientId: String, spec: Option[String]) = WebSocket.async[JsValue] { implicit request =>
    if (request.session.get("userName").isDefined) {
      val organizationConfiguration = OrganizationConfigurationHandler.getByDomain(request.domain)
      DataSetEventFeed.subscribe(orgId, clientId, session.get("userName").get, organizationConfiguration.orgId, spec)
    } else {
      // return a fake pair
      // TODO perhaps a better way here ?
      Promise.pure((Done[JsValue, JsValue](JsString(""), Input.Empty), Concurrent.broadcast._1))
    }
  }

  def listAsTokens(q: String, formats: Seq[String]) = Root {
    Action {
      implicit request =>
        val query = MongoDBObject("spec" -> Pattern.compile(q, Pattern.CASE_INSENSITIVE))
        val sets = DataSet.dao.find(query).filter { set =>
          formats.isEmpty ||
            formats.toSet.subsetOf(set.getPublishableMappingSchemas.map(_.getPrefix).toSet)
        }
        val asTokens = sets.map(set => Token(set.spec, set.spec)).toList
        Json(asTokens)
    }
  }

}
