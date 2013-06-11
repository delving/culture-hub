package controllers

import core.storage.{ FileUploadResponse, FileStorage }
import play.api.mvc._
import com.mongodb.casbah.Imports._
import com.novus.salat._
import dao.SalatDAO
import com.novus.salat.{ TypeHintFrequency, Context }
import json.{ StringObjectIdStrategy, JSONConfig }
import models.OrganizationConfiguration
import com.mongodb.casbah.commons.MongoDBObject
import play.api.data.Form
import scala.collection.JavaConverters._
import models.HubMongoContext._
import com.novus.salat.StringTypeHintStrategy
import extensions.MissingLibs
import play.api.i18n.Messages
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.{ JsonMethods, Printer }

/**
 * Experimental CRUD controller, for the admin part of the site.
 *
 * The idea is to provide a number of generic methods handling the listing, submission (create or update), and deletion of a model.
 *
 * TODO provide a way to override the default "name" field, which is used in the list action for deletion
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
  def emptyModel(implicit request: RequestHeader, configuration: OrganizationConfiguration): Model

  /**
   * The DAO used to persist the domain model
   */
  def dao(implicit configuration: OrganizationConfiguration): D

  // ~~~ override the following to customize

  def fileUploadEnabled = false

  def updateHandler(onUpdate: Option[(Model, Model) => Model])(submitted: Model, persisted: Model)(implicit request: Request[AnyContent], configuration: OrganizationConfiguration,
    mom: Manifest[Model], mod: Manifest[D]) = {

    log.debug("Update handler invoked")
    onUpdate.map { u =>
      val contextualized = u(submitted, persisted)
      log.debug("Saving contextualized object: " + contextualized.toString)
      dao.save(contextualized)
    }.getOrElse {
      log.debug("Saving automatic object: " + submitted.toString)
      // TODO this should, in fact, be a merge, if somehow possible.
      // It may be that the persisted state of the item changes while it is loaded (e.g. AJAX update, different user, ...)
      dao.save(submitted)
    }
    Right(submitted)
  }

  def creationHandler(onCreate: Option[Model => Model])(model: Model)(implicit request: Request[AnyContent], configuration: OrganizationConfiguration,
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
  def baseUrl(implicit configuration: OrganizationConfiguration): String = "/admin/%s".format(urlPath)

  // ~~~ default actions, override if necessary

  def view(id: ObjectId,
    titleKey: String = "",
    viewTemplate: String = "organization/crudView.html",
    fields: Seq[(String, String)] = Seq(("hubb.Name" -> "name")))(implicit mom: Manifest[Model], mod: Manifest[D]) = OrganizationAdmin {
    Action {

      implicit request =>
        crudView(id, titleKey, viewTemplate, fields)

    }
  }

  def list(titleKey: String = "",
    listTemplate: String = "organization/crudList.html",
    fields: Seq[(String, String)] = Seq(("hubb.Name" -> "name")),
    additionalActions: Seq[ListAction] = Seq.empty,
    isAdmin: RequestHeader => Boolean = { Unit => true },
    filter: Seq[(String, Any)] = Seq.empty)(implicit mom: Manifest[Model], mod: Manifest[D]) = OrganizationAdmin {
    Action {
      implicit request =>
        crudList(titleKey, listTemplate, fields, true, true, true, Seq.empty, additionalActions, isAdmin(request), filter)

    }
  }

  def update(id: Option[ObjectId],
    templateName: Option[String] = None,
    additionalTemplateData: Option[(Option[Model] => Seq[(Symbol, AnyRef)])] = None)(implicit mom: Manifest[Model], mod: Manifest[D]) = OrganizationAdmin {
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

  def upload(id: String, uid: String) = OrganizationAdmin {
    Action {
      implicit request =>
        crudUpload(id, uid)
    }
  }

  // ~~~ CRUD handler methods

  def crudView(id: ObjectId,
    titleKey: String = "",
    viewTemplate: String = "organization/crudView.html",
    fields: Seq[(String, String)] = Seq(("hubb.Name" -> "name")))(implicit request: RequestHeader, configuration: OrganizationConfiguration,
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

  def crudList(titleKey: String = "",
    listTemplate: String = "organization/crudList.html",
    fields: Seq[(String, String)] = Seq(("hubb.Name" -> "name")),
    createActionEnabled: Boolean = true,
    editActionEnabled: Boolean = true,
    deleteActionEnabled: Boolean = true,
    additionalTemplateData: Seq[(Symbol, AnyRef)] = Seq.empty,
    additionalActions: Seq[ListAction] = Seq.empty,
    isAdmin: Boolean = true,
    filter: Seq[(String, Any)] = Seq.empty,
    contextualizer: Option[Model => CaseClass] = None,
    customViewLink: Option[(String, Seq[String])] = None)(implicit request: RequestHeader, configuration: OrganizationConfiguration,
      mom: Manifest[Model], mod: Manifest[D]): Result = {

    val items = dao.find(MongoDBObject(filter: _*)).toSeq

    val contextualizedItems = if (contextualizer.isDefined) {
      items.map(contextualizer.get)
    } else {
      items
    }.toSeq

    val (viewLink, viewLinkParams) = if (customViewLink.isDefined) {
      customViewLink.get
    } else {
      (baseUrl + "/_id_", Seq("id"))
    }

    if (request.queryString.get("format").map(_.exists(_ == "json")).getOrElse(false)) {
      Json(Map("items" -> contextualizedItems)).withHeaders(CACHE_CONTROL -> "no-cache")
    } else {

      Ok(
        Template(
          listTemplate,
          (Seq(
            'titleKey -> title(titleKey),
            'menuKey -> menuKey,
            'viewLink -> viewLink,
            'viewLinkParams -> viewLinkParams,
            'columnLabels -> fields.map(_._1),
            'columnFields -> fields.map(_._2),
            'createActionEnabled -> createActionEnabled,
            'editActionEnabled -> editActionEnabled,
            'deleteActionEnabled -> deleteActionEnabled,
            'additionalActions -> additionalActions.asJava,
            'isAdmin -> isAdmin,
            'baseUrl -> baseUrl) ++ additionalTemplateData): _*
        )
      )
    }
  }

  def crudUpdate(id: Option[ObjectId], templateName: Option[String] = None, additionalTemplateData: Option[(Option[Model] => Seq[(Symbol, AnyRef)])])(implicit request: RequestHeader, configuration: OrganizationConfiguration,
    mom: Manifest[Model], mod: Manifest[D]): Result = {

    implicit val formats = DefaultFormats

    val resolvedTemplateName = templateName.getOrElse("organization/" + className + "s/update.html")

    implicit val ctx = new Context {
      val name = "json-context"
      override val typeHintStrategy = StringTypeHintStrategy(when = TypeHintFrequency.WhenNecessary, typeHint = "_t")
      override val jsonConfig: JSONConfig = JSONConfig(objectIdStrategy = StringObjectIdStrategy)
    }

    def computeTemplateData(item: Option[Model], json: String): Seq[(Symbol, AnyRef)] = {
      additionalTemplateData.map { data =>
        data(item)
      }.getOrElse {
        Seq.empty
      } ++ Seq(
        'baseUrl -> baseUrl,
        'menuKey -> menuKey,
        'data -> json,
        'fileUploadEnabled -> fileUploadEnabled.asInstanceOf[AnyRef],
        'uid -> MissingLibs.UUID) ++
        item.map(it => Seq('id -> it.id)).getOrElse(Seq.empty)
    }

    def serializeToJson(item: Model, isCreated: Boolean): String = {
      val serializedItem: JObject = grater[Model].toJSON(item)
      val files: Option[JObject] = if (fileUploadEnabled) {
        val files = core.storage.FileStorage.listFiles(item.id.toString).map(f => FileUploadResponse(f))
        val serializedFiles = JObject(List(JField("files", Extraction.decompose(files))))
        Some(serializedFiles)
      } else {
        None
      }
      val creationTag: Option[JObject] = if (isCreated) Some(JObject(List(JField("_created_", JBool(true))))) else None

      val merged = Seq(files, creationTag).filterNot(_.isEmpty).map(_.get).foldLeft(serializedItem) { _ merge _ }
      Printer.compact(JsonMethods.render(merged))
    }

    id.map { _id =>
      val item = dao.findOneById(_id)
      if (item == None) {
        NotFound("Item with ID %s wasn't found".format(_id))
      } else {
        val json = serializeToJson(item.get, isCreated = false)
        val templateData = computeTemplateData(item, json)
        Ok(Template(resolvedTemplateName, templateData: _*))
      }
    }.getOrElse {
      val json = serializeToJson(emptyModel, isCreated = true)
      log.debug(json)
      val templateData = computeTemplateData(None, json)
      Ok(Template(resolvedTemplateName, templateData: _*))
    }

  }

  def crudSubmit(onUpdate: Option[(Model, Model) => Model] = None,
    onCreate: Option[Model => Model] = None)(implicit request: Request[AnyContent], configuration: OrganizationConfiguration,
      mom: Manifest[Model], mod: Manifest[D]): Result = {

    handleSubmit(form, dao.findOneById, updateHandler(onUpdate), creationHandler(onCreate))

  }

  def crudDelete(id: ObjectId, onDelete: Option[Model => Unit] = None)(implicit request: Request[AnyContent], configuration: OrganizationConfiguration,
    mom: Manifest[Model], mod: Manifest[D]): Result = {

    dao.findOneById(id).map { item =>
      if (onDelete.isDefined) {
        onDelete.get(item)
      }
      dao.remove(item)

      if (fileUploadEnabled) {
        val files = FileStorage.listFiles(id.toString)
        files.foreach { f => FileStorage.deleteFile(f.id.toString) }
      }

      Ok
    }.getOrElse {
      NotFound
    }

  }

  def crudUpload(id: String, uid: String)(implicit configuration: OrganizationConfiguration): Result = {
    log.debug("Attaching upload with UID %s to item with ID %s".format(uid, id))
    FileStorage.markFilesAttached(uid, id)
    Ok
  }

  private def title(titleKey: String)(implicit mom: Manifest[Model]) = if (titleKey.isEmpty) {
    splitCamelCase(className) + "s"
  } else {
    Messages(titleKey)
  }

  private def className(implicit mom: Manifest[Model]) = mom.runtimeClass.getName.split("\\.").lastOption.getOrElse(mom.runtimeClass.getName)

  // ~~ misc

  protected def splitCamelCase(s: String) = s.replaceAll(
    String.format("%s|%s|%s",
      "(?<=[A-Z])(?=[A-Z][a-z])",
      "(?<=[^A-Z])(?=[A-Z])",
      "(?<=[A-Za-z])(?=[^A-Za-z])"), " ")

  case class ListAction(
    actionType: String = "link",
    actionClass: String = "edit",
    labelKey: String,
    url: String,
    urlFields: Seq[String] = Seq("id"),
    isAdminAction: Boolean = false)

}