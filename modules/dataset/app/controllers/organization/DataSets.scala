package controllers.organization

import models.DataSet
import play.api.i18n.Messages
import com.mongodb.casbah.Imports._
import controllers.OrganizationController
import java.util.regex.Pattern
import play.api.libs.json.JsValue
import play.api.libs.concurrent.{ Akka, Promise }
import play.api.libs.iteratee._
import com.escalatesoft.subcut.inject.BindingModule
import play.api.libs.Comet
import play.api.libs.concurrent.Execution.Implicits._
import controllers.Token
import scala.util.Random
import controllers.organization.DataSetEventFeed.ClientMessage
import play.api.Play.current

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSets(implicit val bindingModule: BindingModule) extends OrganizationController {

  def list = OrganizationMember {
    MultitenantAction {
      implicit request =>

        val clientId = Random.nextInt(10000)

        render {
          case Accepts.Html() =>
            Ok(
              Template(
                'title -> listPageTitle("dataset"),
                'canAdministrate -> DataSet.dao.canAdministrate(connectedUser),
                'clientId -> clientId
              )
            )
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
          val clientId = Random.nextInt(10000)
          Ok(Template('spec -> ds.spec, 'clientId -> clientId))
        }
    }
  }

  def feed(clientId: String, spec: Option[String]) = MultitenantAction { implicit request =>
    Async {
      if (request.session.get("userName").isDefined) {
        val eventuallyChannel = DataSetEventFeed.subscribe(configuration.orgId, clientId, session.get("userName").get, configuration.orgId, spec)
        eventuallyChannel map {
          case (i: Iteratee[JsValue, _], e: Enumerator[JsValue]) =>
            Ok.stream(e &> Comet(callback = "parent.onMessage"))
        }
      } else {
        Promise.pure {
          val nothing = Enumerator("") >>> Enumerator.eof
          Ok.stream(nothing &> Comet(callback = "parent.onMessage"))
        }
      }
    }
  }

  def dataSetEventFeed = Akka.system.actorFor("akka://application/user/plugin-dataSet/dataSetEventFeed")

  def command(clientId: String) = MultitenantAction(parse.tolerantFormUrlEncoded) { implicit request =>
    log.debug(s"Received command from $clientId: ${request.body}")
    val eventType = request.body.get("eventType").flatMap(_.headOption).getOrElse("")
    val spec = request.body.get("payload").flatMap(_.headOption).getOrElse("")

    dataSetEventFeed ! ClientMessage(clientId, eventType, spec)
    Ok
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