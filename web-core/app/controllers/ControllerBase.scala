package controllers

import com.novus.salat
import core.storage.{ FileStorage, StoredFile }
import play.api.data.Form
import org.bson.types.ObjectId
import play.api.mvc._
import play.api.i18n.Messages
import play.api.data.Forms._
import extensions.Extensions
import play.api.data.FormError

/**
 * Base controller methods
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait ControllerBase extends Extensions with MultitenancySupport with Logging { self: Controller =>

  /**
   * Handles the submission of a form for creation or update
   * @param form the [[play.api.data.Form]] being submitted
   * @param findOneById finds a Model by ID
   * @param update updates an existing Model
   * @param create creates a new Model
   * @tparam ViewModel the type of the ViewModel, which is submitted by the view
   * @tparam Model the type of the domain Model
   * @return a [[play.api.mvc.Result]]
   */
  def handleSubmit[ViewModel <: salat.CaseClass, Model <: salat.CaseClass](form: Form[ViewModel],
    findOneById: ObjectId => Option[Model],
    update: (ViewModel, Model) => Either[String, ViewModel],
    create: ViewModel => Either[String, ViewModel])(implicit request: MultitenantRequest[AnyContent], mf: Manifest[Model]): Result = {

    log.debug("Invoked submit handler")

    // retrieve the id separately. This way we do not impose on the bound ViewModel to have an id attribute.
    def extractId(field: String): Option[ObjectId] = request.body.asJson.flatMap { body =>
      (body \ field).asOpt[String].flatMap { oid =>
        if (ObjectId.isValid(oid)) Some(new ObjectId(oid)) else None
      }
    }

    val maybeId: Option[ObjectId] = extractId("id").orElse(extractId("_id"))

    val isNew = request.body.asJson.map { body =>
      (body \ "_created_").asOpt[Boolean].isDefined
    }.getOrElse(false)

    log.debug("Is new object: " + isNew)

    form.bind(request.body.asJson.get).fold(
      formWithErrors => handleValidationError(formWithErrors),
      boundViewModel => {

        def doCreate() = create(boundViewModel) match {
          case Right(createdViewModel) =>
            info("Created new model based on: " + createdViewModel.toString)
            Json(createdViewModel)
          case Left(errorMessage) =>
            warning("Failed to create new item because of the following errors: " + errorMessage)
            Json(Map("errors" -> Map("global" -> errorMessage)))
        }

        maybeId match {
          case Some(id) if !isNew =>
            findOneById(id) match {
              case Some(existingModel) =>
                try {
                  update(boundViewModel, existingModel) match {
                    case Right(updatedViewModel) =>
                      info("Updated '%s' with identifier %s".format(mf.runtimeClass.getName, id))
                      Json(updatedViewModel)
                    case Left(errorMessage) =>
                      warning("Problem while updating '%s' with identifier %s: %s".format(mf.runtimeClass.getName, id, errorMessage))
                      // TODO see if there's a way to abstract the copy method and return the full initial object.
                      Json(Map("errors" -> Map("global" -> errorMessage)))
                  }
                } catch {
                  case t: Throwable =>
                    logError(t, "Problem while updating '%s' with identifier %s".format(mf.runtimeClass.getName, id))
                    // Json(boundForm.copy(errors = Map("global" -> t.getMessage)))
                    // TODO see if there's a way to abstract the copy method and return the full initial object.
                    Json(Map("errors" -> Map("global" -> t.getMessage)))
                }
              case None =>
                Error("Model of type '%s' was not found for identifier %s".format(mf.runtimeClass.getName, id))
            }
          case None => doCreate()
          case Some(defaultId) => doCreate()
        }
      }
    )

  }

  // ~~~ turns validation errors into a JSON response. This returns a map of error messages

  def handleValidationError[T](form: Form[T])(implicit request: RequestHeader) = {
    val e: Seq[FormError] = form.errors
    val fieldErrors = e.filterNot(_.key.isEmpty).map(error => (error.key.replaceAll("\\.", "_"), Messages(error.message, error.args))).toMap
    val globalErrors = e.filter(_.key.isEmpty).map(error => ("global", Messages(error.message, error.args))).toMap
    Json(Map("errors" -> (fieldErrors ++ globalErrors)), BAD_REQUEST)
  }

  // ~~~ Utilities

  def slugify(str: String): String = {
    import java.text.Normalizer
    Normalizer.normalize(str, Normalizer.Form.NFD).replaceAll("[^\\w ]", "").replace(" ", "-").toLowerCase
  }

}