package controllers

import play.api.mvc._
import play.api.data.Form
import play.api.i18n.Messages
import play.api.data.Forms._
import extensions.Extensions
import org.bson.types.ObjectId
import com.novus.salat
import play.api.data.FormError
import models.{MultiModel, DomainConfiguration}
import salat.dao.SalatDAO
import com.mongodb.casbah.commons.MongoDBObject
import eu.delving.templates.scala.GroovyTemplates

/**
 * Experimental CRUD controller.
 * The idea is to provide a number of generic methods handling the listing, submission (create or update), and deletion of a model.
 *
 * TODO see how to handle the viewModel.copy(errors = ...) case.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait CRUDController extends Logging with Extensions with RenderingExtensions { self: Controller with GroovyTemplates with DomainConfigurationAware =>

  /**
   * The menu key for the actions of this CRUD controller.
   * In the future, the navigation should be handled transparently via routing.
   */
  def menuKey: Option[String] = None

  def crudList[Model <: salat.CaseClass, D <: SalatDAO[Model, ObjectId]](dao: D, titleKey: String = "", listTemplate: String = "organization/crudList.html", filter: Seq[(String, String)] = Seq.empty)
                                                                        (implicit request: RequestHeader, configuration: DomainConfiguration,
                                                                          mom: Manifest[Model], mod: Manifest[D]): Result = {
    val items = dao.find(MongoDBObject(filter : _*)).toSeq

    log.debug(request.accept.mkString(", "))
    log.debug(request.accepts(JSON).toString)
    log.debug(request.accepts("application/json").toString)
    log.debug(request.accepts(HTML).toString)

    if (request.accepts("application/json") && !request.accepts(HTML)) {
      Json(Map("items" -> items))
    } else {

      val tKey = if (titleKey.isEmpty) {
        splitCamelCase(mom.erasure.getName.split("\\.").lastOption.getOrElse(mom.erasure.getName)) + "s"
      } else {
        titleKey
      }

      Ok(Template(listTemplate, 'titleKey -> tKey, 'menuKey -> menuKey.getOrElse("")))
    }
  }

  def splitCamelCase(s: String) = s.replaceAll(
    String.format("%s|%s|%s",
                  "(?<=[A-Z])(?=[A-Z][a-z])",
                  "(?<=[^A-Z])(?=[A-Z])",
                  "(?<=[A-Za-z])(?=[^A-Za-z])"), " ")

  /**
   * Handles the submission of a form for creation or update
   * @param form the [[play.api.data.Form]] being submitted
   * @param findOneById finds a Model by ID
   * @param update updates an existing Model
   * @param create creates a new Model
   * @tparam ViewModel the type of the ViewModel
   * @tparam A the type of the Model
   * @return a [[play.api.mvc.Result]]
   */
  def handleSubmit[ViewModel <: CRUDViewModel, A <: salat.CaseClass]
                  (form: Form[ViewModel], findOneById: ObjectId => Option[A], update: (ViewModel, A) => Either[String, ViewModel], create: ViewModel => Either[String, ViewModel])
                  (implicit request: Request[AnyContent], mf: Manifest[A]): Result = {

    form.bind(request.body.asJson.get).fold(
      formWithErrors => handleValidationError(formWithErrors),
      boundViewModel => {
        boundViewModel.id match {
          case Some(id) =>
            findOneById(id) match {
              case Some(existingModel) =>
                try {
                  update(boundViewModel, existingModel) match {
                    case Right(updatedViewModel) =>
                      info("Updated 's%' with identifier %s".format(mf.erasure.getName, id))
                      Json(updatedViewModel)
                    case Left(errorMessage) =>
                      warning("Problem while updating '%s' with identifier %s: %s".format(mf.erasure.getName, id, errorMessage))
                      // TODO see if there's a way to abstract the copy method and return the full initial object.
                      Json(Map("errors" -> Map("global" -> errorMessage)))
                  }
                } catch {
                  case t: Throwable =>
                    logError(t, "Problem while updating '%s' with identifier %s".format(mf.erasure.getName, id))
                    // Json(boundForm.copy(errors = Map("global" -> t.getMessage)))
                    // TODO see if there's a way to abstract the copy method and return the full initial object.
                    Json(Map("errors" -> Map("global" -> t.getMessage)))
                }
              case None =>
                Error("Model of type '%s' was not found for identifier %s".format(mf.erasure.getName, id))
            }
          case None =>
            create(boundViewModel) match {
              case Right(createdViewModel) =>
                Json(createdViewModel)
              case Left(errorMessage) =>
                Json(Map("errors" -> Map("global" -> errorMessage)))
            }
        }
      }
    )



  }

    // ~~~ form handling when using knockout. This returns a map of error messages

  def handleValidationError[T](form: Form[T])(implicit request: RequestHeader) = {
    val e: Seq[FormError] = form.errors
    val fieldErrors = e.filterNot(_.key.isEmpty).map(error => (error.key.replaceAll("\\.", "_"), Messages(error.message, error.args))).toMap
    val globalErrors = e.filter(_.key.isEmpty).map(error => ("global", Messages(error.message, error.args))).toMap
    Json(Map("errors" -> (fieldErrors ++ globalErrors)), BAD_REQUEST)
  }

  // ~~~ Form utilities
  import extensions.Formatters._

  val tokenListMapping = seq(
    play.api.data.Forms.mapping(
      "id" -> text,
      "name" -> text,
      "tokenType" -> optional(text),
      "data" -> optional(of[Map[String, String]])
      )(Token.apply)(Token.unapply)
    )

}

abstract class CRUDViewModel extends ViewModel {
  val id: Option[ObjectId]
}
