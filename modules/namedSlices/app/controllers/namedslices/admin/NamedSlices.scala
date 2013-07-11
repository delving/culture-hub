package controllers.namedslices.admin

import controllers.{ CRUDController, OrganizationController }
import models._
import com.escalatesoft.subcut.inject.BindingModule
import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._
import com.mongodb.casbah.Imports._
import play.api.data.validation.Constraints
import models.NamedSliceQuery
import models.cms.CMSPage

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class NamedSlices(implicit val bindingModule: BindingModule) extends OrganizationController with CRUDController[NamedSlice, NamedSliceDAO] {

  def urlPath: String = "namedSlices"

  def menuKey: String = "namedSlice"

  def form(implicit mom: Manifest[NamedSlice]): Form[NamedSlice] = Form(
    mapping(
      "_id" -> of[ObjectId],
      "key" -> nonEmptyText.verifying(Constraints.pattern("^[A-Za-z0-9-]{3,40}$".r, "constraint.validSpec", "namedslice.invalidKeyFormat")),
      "name" -> nonEmptyText,
      "cmsPageKey" -> nonEmptyText,
      "query" -> mapping(
        "terms" -> nonEmptyText,
        "dataSets" -> seq(text)
      )(NamedSliceQuery.apply)(NamedSliceQuery.unapply),
      "published" -> boolean
    )(NamedSlice.apply)(NamedSlice.unapply)
  )

  def emptyModel[A](implicit request: MultitenantRequest[A], configuration: OrganizationConfiguration): NamedSlice =
    NamedSlice(key = "", name = "", cmsPageKey = "", query = NamedSliceQuery(terms = ""), published = true)

  def dao(implicit configuration: OrganizationConfiguration): NamedSliceDAO = NamedSlice.dao

  def list = OrganizationAdmin {
    implicit request =>
      crudList(customViewLink = Some(("/slices/_key_", Seq("key"))))
  }

  def add = OrganizationAdmin {
    implicit request =>
      crudUpdate(None, additionalTemplateData = Some(creationPageTemplateData))
  }

  def update(id: ObjectId) = OrganizationAdmin {
    implicit request =>
      crudUpdate(Some(id), additionalTemplateData = Some(creationPageTemplateData))
  }1

  private def creationPageTemplateData(implicit request: MultitenantRequest[AnyContent], configuration: OrganizationConfiguration) = {
    val pages = CMSPage.dao.list(getLang, None).filter(_.published).map { page => (page.key, page.title) }
    val dataSets = DataSet.dao.findAll().map { set => (set.spec, set.details.name) }

    {
      model: Option[NamedSlice] =>
        Seq(
          'cmsPages -> pages,
          'dataSets -> dataSets
        )
    }

  }

}
