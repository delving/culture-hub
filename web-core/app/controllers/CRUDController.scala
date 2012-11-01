package controllers

import play.api.mvc._
import org.bson.types.ObjectId
import com.novus.salat._
import json.{StringObjectIdStrategy, JSONConfig}
import models.DomainConfiguration
import dao.SalatDAO
import com.mongodb.casbah.commons.MongoDBObject
import eu.delving.templates.scala.GroovyTemplates
import play.api.data.Form
import net.liftweb.json._
import net.liftweb.json.JsonAST.JField
import models.HubMongoContext._
import com.novus.salat.{TypeHintFrequency, StringTypeHintStrategy, Context}

/**
 * Experimental CRUD controller.
 * The idea is to provide a number of generic methods handling the listing, submission (create or update), and deletion of a model.
 *
 * TODO customize list fields
 * TODO link to view page
 * TODO view page
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait CRUDController[Model <: CaseClass { def id: ObjectId }, D <: SalatDAO[Model, ObjectId]] extends ControllerBase { self: Controller with GroovyTemplates with DomainConfigurationAware =>

  def baseUrl(implicit request: RequestHeader, configuration: DomainConfiguration): String

  /**
   * The menu key for the actions of this CRUD controller.
   * In the future, the navigation should be handled transparently via routing.
   */
  def menuKey: String

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
      dao.save(submitted)
    }
    Right(submitted)
  }

  def creationHandler(onCreate: Option[Model => Model])(model: Model)(implicit request: Request[AnyContent], configuration: DomainConfiguration,
                                             mom: Manifest[Model], mod: Manifest[D]) = {
    onCreate.map { c =>
      val contextualized = c(model)
      dao.insert(contextualized)
    }.getOrElse {
      dao.insert(model)
    }
    Right(model)
  }



//  // ~~~ default actions
//
//  def view(id: ObjectId) = Action {
//    implicit request =>
//      crudView(id)
//  }
//
//
//  def list = Action {
//    implicit request =>
//      crudList()
//  }
//
//  def update(id: Option[ObjectId]) = Action {
//    implicit request =>
//      crudUpdate(id)
//  }
//
//  def submit = Action {
//    implicit request =>
//      crudSubmit
//  }


  // ~~~ CRUD handler methods

  def crudView(id: ObjectId)(implicit request: RequestHeader, configuration: DomainConfiguration,
                                      mom: Manifest[Model], mod: Manifest[D]): Result = {
    dao.findOneById(id).map { item =>
      Json(item)
    }.getOrElse {
      NotFound("Could not find item with ID " + id)
    }
  }

  def crudList(titleKey: String = "", listTemplate: String = "organization/crudList.html", filter: Seq[(String, String)] = Seq.empty)
              (implicit request: RequestHeader, configuration: DomainConfiguration,
                        mom: Manifest[Model], mod: Manifest[D]): Result = {

    val items = dao.find(MongoDBObject(filter : _*)).toSeq

    log.debug(request.accept.mkString(", "))
    log.debug(request.accepts(JSON).toString)
    log.debug(request.accepts("application/json").toString)
    log.debug(request.accepts(HTML).toString)

    if (acceptsJson) {
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

  def crudUpdate(id: Option[ObjectId])(implicit request: RequestHeader, configuration: DomainConfiguration,
                                                mom: Manifest[Model], mod: Manifest[D]): Result = {

    implicit val formats = DefaultFormats

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
        Ok(Template('baseUrl -> baseUrl, 'data -> grater[Model].toCompactJSON(item.get)))
      }
    }.getOrElse {
      val json: JObject = grater[Model].toJSON(emptyModel)
      val jsonItem = json merge JObject(List(JField("_created_", JBool(true))))
      val rendered = Printer.compact(JsonAST.render(jsonItem))
      log.debug(rendered)
      Ok(Template('baseUrl -> baseUrl, 'data -> rendered))
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



  // ~~ misc

  protected def splitCamelCase(s: String) = s.replaceAll(
    String.format("%s|%s|%s",
                  "(?<=[A-Z])(?=[A-Z][a-z])",
                  "(?<=[^A-Z])(?=[A-Z])",
                  "(?<=[A-Za-z])(?=[^A-Za-z])"), " ")

}