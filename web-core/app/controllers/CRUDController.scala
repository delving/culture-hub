package controllers

import play.api.mvc._
import com.mongodb.casbah.Imports._
import com.novus.salat._
import dao.SalatDAO
import com.novus.salat.{TypeHintFrequency, StringTypeHintStrategy, Context}
import json.{StringObjectIdStrategy, JSONConfig}
import models.DomainConfiguration
import com.mongodb.casbah.commons.MongoDBObject
import play.api.data.Form
import net.liftweb.json._
import net.liftweb.json.JsonAST.JField
import scala.collection.JavaConverters._
import models.HubMongoContext._


/**
 * Experimental CRUD controller, for the admin part of the site.
 *
 * The idea is to provide a number of generic methods handling the listing, submission (create or update), and deletion of a model.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait CRUDController[Model <: CaseClass { def id: ObjectId }, D <: SalatDAO[Model, ObjectId]] extends ControllerBase { self: OrganizationController =>

  // ~~~ Navigation

  /**
   * URL path in the administration namespace for this controller (e.g. "cms", "organizations", ...)
   */
  def urlPath: String

  /**
   * The menu key for the actions of this CRUD controller.
   * In the future, the navigation should be handled transparently via routing.
   */
  def menuKey: String

  // ~~~ Binding

  /**
   * The Play Form definition, including constraints
   */
  def form(implicit mom: Manifest[Model]): Form[Model]

  /**
   * Returns an empty model class, for entity creation
   */
  def emptyModel(implicit configuration: DomainConfiguration): Model

  /**
   * The DAO used to persist the domain model
   */
  def dao(implicit configuration: DomainConfiguration): D


  // ~~~ override the following to customize

  def updateHandler(onUpdate: Option[(Model, Model) => Model])(submitted: Model, persisted: Model)
                   (implicit request: Request[AnyContent], configuration: DomainConfiguration,
                             mom: Manifest[Model], mod: Manifest[D]) = {
    onUpdate.map { u =>
      val contextualized = u(submitted, persisted)
      dao.save(contextualized)
    }.getOrElse {
      // TODO this should, in fact, be a merge, if somehow possible.
      // It may be that the persisted state of the item changes while it is loaded (e.g. AJAX update, different user, ...)
      dao.save(submitted)
    }
    Right(submitted)
  }

  def creationHandler(onCreate: Option[Model => Model])(model: Model)
                     (implicit request: Request[AnyContent], configuration: DomainConfiguration,
                               mom: Manifest[Model], mod: Manifest[D]) = {
    onCreate.map { c =>
      val contextualized = c(model)
      dao.insert(contextualized)
    }.getOrElse {
      dao.insert(model)
    }
    Right(model)
  }


  /**
   * Base URL of all actions for this CRUD model
   */
  def baseUrl(implicit configuration: DomainConfiguration): String = "/organizations/%s/%s".format(configuration.orgId, urlPath)

  // ~~~ default actions, override if necessary

  def view(id: ObjectId,
           titleKey: String = "",
           viewTemplate: String = "organization/crudView.html",
           fields: Seq[(String, String)] = Seq(("thing.name" -> "name")))
          (implicit mom: Manifest[Model], mod: Manifest[D]) = OrganizationAdmin {
    Action {

      implicit request =>
        crudView(id, titleKey, viewTemplate, fields)

    }
  }

  def list(titleKey: String = "",
           listTemplate: String = "organization/crudList.html",
           fields: Seq[(String, String)] = Seq(("thing.name" -> "name")),
           additionalActions: Seq[ListAction] = Seq.empty,
           isAdmin: RequestHeader => Boolean = { Unit => true },
           filter: Seq[(String, String)] = Seq.empty)
          (implicit mom: Manifest[Model], mod: Manifest[D]) = OrganizationAdmin {
    Action {
      implicit request =>
        crudList(titleKey, listTemplate, fields, additionalActions, isAdmin(request), filter)

    }
  }

  def update(id: Option[ObjectId],
             templateName: Option[String] = None,
             additionalTemplateData: Option[(Option[Model] => Seq[(Symbol, AnyRef)])] = None)
            (implicit mom: Manifest[Model], mod: Manifest[D]) = OrganizationAdmin {
    Action {
      implicit request =>
        crudUpdate(id, templateName, additionalTemplateData)
    }
  }

  def submit(implicit mom: Manifest[Model], mod: Manifest[D]) = OrganizationAdmin {
    Action {
      implicit request =>
        crudSubmit()
    }
  }

  def delete(id: ObjectId)(implicit mom: Manifest[Model], mod: Manifest[D]) = OrganizationAdmin {
    Action {
      implicit request =>
        crudDelete(id)
    }
  }


  // ~~~ CRUD handler methods

  def crudView(id: ObjectId, titleKey: String, viewTemplate: String, fields: Seq[(String, String)])
              (implicit request: RequestHeader, configuration: DomainConfiguration,
                        mom: Manifest[Model], mod: Manifest[D]): Result = {

    dao.findOneById(id).map { item =>

      val fieldMap = fields.map(f => Map("labelKey" -> f._1, "field" -> f._2).asJava).toList.asJava

      Ok(
        Template(
          viewTemplate,
          'titleKey -> title(titleKey),
          'fields -> fieldMap,
          'item -> item
        )
      )

    }.getOrElse {
      NotFound("Could not find item with ID " + id)
    }
  }

  def crudList(titleKey: String,
               listTemplate: String,
               fields: Seq[(String, String)],
               additionalActions: Seq[ListAction],
               isAdmin: Boolean,
               filter: Seq[(String, String)])
              (implicit request: RequestHeader, configuration: DomainConfiguration,
                        mom: Manifest[Model], mod: Manifest[D]): Result = {

    val items = dao.find(MongoDBObject(filter : _*)).toSeq

    if (acceptsJson) {
      Json(Map("items" -> items)).withHeaders(CACHE_CONTROL -> "no-cache")
    } else {

      Ok(
        Template(
          listTemplate,
          'titleKey -> title(titleKey),
          'menuKey -> menuKey.getOrElse(""),
          'columnLabels -> fields.map(_._1),
          'columnFields -> fields.map(_._2),
          'additionalActions -> additionalActions.asJava,
          'isAdmin -> isAdmin
        )
      )
    }
  }

  def crudUpdate(id: Option[ObjectId], templateName: Option[String] = None, additionalTemplateData: Option[(Option[Model] => Seq[(Symbol, AnyRef)])])
                (implicit request: RequestHeader, configuration: DomainConfiguration,
                          mom: Manifest[Model], mod: Manifest[D]): Result = {

    implicit val formats = DefaultFormats

    val resolvedTemplateName = templateName.getOrElse("organization/" + className + "s/update.html")

    implicit val ctx = new Context {
      val name = "json-context"
      override val typeHintStrategy = StringTypeHintStrategy(when = TypeHintFrequency.WhenNecessary, typeHint = "_t")
      override val jsonConfig: JSONConfig = JSONConfig(objectIdStrategy = StringObjectIdStrategy)
    }

    id.map { _id =>
      val item = dao.findOneById(_id)
      if (item == None) {
        NotFound("Item with ID %s wasn't found".format(_id))
      } else {
        val baseData = Seq('baseUrl -> baseUrl, 'data -> grater[Model].toCompactJSON(item.get), 'id -> item.get.id)
        if (additionalTemplateData.isDefined) {
          val additionalData = additionalTemplateData.get(item)
          Ok(Template(resolvedTemplateName, (baseData ++ additionalData) :_*))
        } else {
          Ok(Template(resolvedTemplateName, baseData :_*))
        }
      }
    }.getOrElse {
      val json: JObject = grater[Model].toJSON(emptyModel)
      val jsonItem = json merge JObject(List(JField("_created_", JBool(true))))
      val rendered = Printer.compact(JsonAST.render(jsonItem))
      log.debug(rendered)
      val baseData = Seq('baseUrl -> baseUrl, 'data -> rendered)
      if (additionalTemplateData.isDefined) {
        val additionalData = additionalTemplateData.get(None)
        Ok(Template(resolvedTemplateName, (baseData ++ additionalData) :_*))
      } else {
        Ok(Template(resolvedTemplateName, baseData :_*))
      }
    }

  }

  def crudSubmit(onUpdate: Option[(Model, Model) => Model] = None,
                 onCreate: Option[Model => Model] = None)
                (implicit request: Request[AnyContent], configuration: DomainConfiguration,
                          mom: Manifest[Model], mod: Manifest[D]): Result = {

    handleSubmit(form, dao.findOneById, updateHandler(onUpdate), creationHandler(onCreate))

  }

  def crudDelete(id: ObjectId)(implicit request: Request[AnyContent], configuration: DomainConfiguration,
                                        mom: Manifest[Model], mod: Manifest[D]): Result = {

    dao.findOneById(id).map { item =>
      dao.remove(item)
      Ok
    }.getOrElse {
      NotFound
    }

  }

  protected def acceptsJson(implicit request: RequestHeader) = request.accepts("application/json") && !request.accepts(HTML)

  private def title(titleKey: String)(implicit mom: Manifest[Model]) = if (titleKey.isEmpty) {
    splitCamelCase(className) + "s"
  } else {
    titleKey
  }

  private def className(implicit mom: Manifest[Model]) = mom.erasure.getName.split("\\.").lastOption.getOrElse(mom.erasure.getName)

  // ~~ misc

  protected def splitCamelCase(s: String) = s.replaceAll(
    String.format("%s|%s|%s",
                  "(?<=[A-Z])(?=[A-Z][a-z])",
                  "(?<=[^A-Z])(?=[A-Z])",
                  "(?<=[A-Za-z])(?=[^A-Za-z])"), " ")



  case class ListAction(actionType: String, labelKey: String, url: String)

}