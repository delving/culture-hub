package controllers

import play.api.mvc._
import org.bson.types.ObjectId
import com.novus.salat
import models.DomainConfiguration
import salat.dao.SalatDAO
import com.mongodb.casbah.commons.MongoDBObject
import eu.delving.templates.scala.GroovyTemplates
import play.api.data.Form
import extensions.JJson

/**
 * Experimental CRUD controller.
 * The idea is to provide a number of generic methods handling the listing, submission (create or update), and deletion of a model.
 *
 * TODO add / update
 * TODO submit
 * TODO delete
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait CRUDController[Model <: salat.CaseClass, D <: SalatDAO[Model, ObjectId]] extends ControllerBase { self: Controller with GroovyTemplates with DomainConfigurationAware =>

  def baseUrl(implicit request: RequestHeader, configuration: DomainConfiguration): String

  /**
   * The menu key for the actions of this CRUD controller.
   * In the future, the navigation should be handled transparently via routing.
   */
  def menuKey: String

  def form(implicit mom: Manifest[Model]): Form[Model]

  def emptyModel(implicit configuration: DomainConfiguration): Model



  // ~~~ CRUD methods

  def crudView(dao: D, id: ObjectId)(implicit request: RequestHeader, configuration: DomainConfiguration,
                                              mom: Manifest[Model], mod: Manifest[D]): Result = {
    dao.findOneById(id).map { item =>
      Json(item)
    }.getOrElse {
      NotFound("Could not find item with ID " + id)
    }
  }

  def crudList(dao: D, titleKey: String = "", listTemplate: String = "organization/crudList.html", filter: Seq[(String, String)] = Seq.empty)
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

  def crudUpdate(dao: D, id: Option[ObjectId])(implicit request: RequestHeader, configuration: DomainConfiguration,
                                                        mom: Manifest[Model], mod: Manifest[D]): Result = {

    id.map { _id =>
      val item = dao.findOneById(_id)
      if (item == None) {
        NotFound
      } else {
        Ok(Template("organization/crudUpdate.html", 'baseUrl -> baseUrl, 'data -> JJson.generate(item.get)))
      }
    }.getOrElse {
      Ok(Template("organization/crudUpdate.html", 'baseUrl -> baseUrl, 'data -> JJson.generate(emptyModel)))
    }

  }

  protected def acceptsJson(implicit request: RequestHeader) = request.accepts("application/json") && !request.accepts(HTML)


  def splitCamelCase(s: String) = s.replaceAll(
    String.format("%s|%s|%s",
                  "(?<=[A-Z])(?=[A-Z][a-z])",
                  "(?<=[^A-Z])(?=[A-Z])",
                  "(?<=[A-Za-z])(?=[^A-Za-z])"), " ")


}