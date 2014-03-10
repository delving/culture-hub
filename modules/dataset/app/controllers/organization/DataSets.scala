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
import com.escalatesoft.subcut.inject.BindingModule

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSets(implicit val bindingModule: BindingModule) extends OrganizationController {

  def sipCreator = OrganizationMember {
    MultitenantAction {
      implicit request => Ok(Template('orgId -> configuration.orgId))
    }
  }

  def list = OrganizationMember {
    MultitenantAction {
      implicit request =>

        render {
          case Accepts.Html() => Ok(Template('title -> listPageTitle("dataset"), 'canAdministrate -> DataSet.dao.canAdministrate(connectedUser)))
          case Accepts.Json() =>
            val sets = DataSet.dao.findAll()
            val smallSets = sets.map { set =>
              Map("key" -> set.spec, "name" -> set.details.name)
            }
            Json(smallSets)
        }

    }
  }

  def dataSet(spec: String) = OrganizationMember {
    MultitenantAction {
      implicit request =>
        val maybeDataSet = DataSet.dao.findBySpecAndOrgId(spec, configuration.orgId)
        if (maybeDataSet.isEmpty) {
          NotFound(Messages("dataset.DatasetWasNotFound", spec))
        } else {
          val ds = maybeDataSet.get
          Ok(Template('spec -> ds.spec))
        }
    }
  }

  def feed(clientId: String, spec: Option[String]) = WebSocket.async[JsValue] { implicit request =>
    if (request.session.get("userName").isDefined) {
      OrganizationConfigurationHandler.getByDomain(request.domain) map { implicit configuration =>
        DataSetEventFeed.subscribe(configuration.orgId, clientId, session.get("userName").get, configuration.orgId, spec)
      } getOrElse {
        Promise.pure((Done[JsValue, JsValue](JsString(""), Input.Empty), Concurrent.broadcast._1))
      }
    } else {
      // return a fake pair
      // TODO perhaps a better way here ?
      Promise.pure((Done[JsValue, JsValue](JsString(""), Input.Empty), Concurrent.broadcast._1))
    }
  }

  def listAsTokens(q: String, maybeFormats: Option[String]) = Root {
    MultitenantAction {
      implicit request =>
        val formats = maybeFormats.map(_.split(",").toSeq.map(_.trim).filterNot(_.isEmpty)).getOrElse(Seq.empty)
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