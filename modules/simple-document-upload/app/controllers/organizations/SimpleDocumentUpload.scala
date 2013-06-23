package controllers.organizations

import play.api.mvc._
import models._
import plugins.SimpleDocumentUploadPlugin
import controllers.OrganizationController
import core._
import extensions.{ MissingLibs, JJson }
import org.bson.types.ObjectId
import play.api.i18n.Messages
import play.api.data.Form
import play.api.data.Forms._
import core.indexing.IndexField._
import core.indexing.IndexField
import models.MetadataItem
import models.MetadataCache
import play.api.data.Forms.mapping
import storage.{ FileStorage, FileUploadResponse, StoredFile }
import controllers.dos.FileUpload
import scala.collection.mutable
import com.escalatesoft.subcut.inject.BindingModule

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class SimpleDocumentUpload(implicit val bindingModule: BindingModule) extends OrganizationController {

  val schemaService = inject[SchemaService]

  def list = OrganizationMember {
    MultitenantAction {
      implicit request =>
        withAccess {

          val items = cache.list().map { item =>
            ListItemViewModel(
              id = item.itemId,
              name = item.getSystemFieldValues(SystemField.TITLE).headOption.getOrElse("Unknown"),
              description = item.getSystemFieldValues(SystemField.DESCRIPTION).headOption.getOrElse("")
            )
          }

          val itemsAsJson = JJson.generate(Map("documents" -> items))

          Ok(Template('data -> itemsAsJson))
        }
    }
  }

  def add = OrganizationMember {
    MultitenantAction {
      implicit request =>
        withAccess {
          val config = SimpleDocumentUploadPlugin.pluginConfiguration
          val generatedId = "%s_%s_%s".format(configuration.orgId, config.collectionName, new ObjectId().toString)
          val itemViewModel = ItemViewModel(
            generatedId,
            config.fields.flatMap { f =>
              for (i <- 0 until f.multiplicity) yield {
                Field(f.key, f.fieldType, Messages("metadata." + f.key.replaceAll(":", ".")), "", f.hasOptions, f.options)
              }
            }
          )
          val itemViewModelAsJson = JJson.generate(itemViewModel)

          Ok(Template("organizations/SimpleDocumentUpload/simpleDocumentUpload.html", 'data -> itemViewModelAsJson, 'uid -> MissingLibs.UUID, 'id -> generatedId))

        }
    }
  }

  def update(itemId: String) = OrganizationMember {
    MultitenantAction {
      implicit request =>
        withAccess {

          val config = SimpleDocumentUploadPlugin.pluginConfiguration

          cache.findOne(itemId).map { item =>

            item.xml.get(config.schemaPrefix).map { rawXml =>

              try {
                val document = scala.xml.XML.loadString(rawXml)

                val fields: Seq[Field] = config.fields.flatMap { field =>
                  val Array(prefix, label) = field.key.split(":")
                  val maybeField = document.child.filter(node => node.prefix == prefix && node.label == label)
                  val existing = maybeField.map { fieldValue =>
                    Field(field.key, field.fieldType, Messages("metadata." + field.key.replaceAll(":", ".")), fieldValue.text, field.hasOptions, field.options)
                  }
                  val delta = field.multiplicity - maybeField.length
                  val additional = if (delta >= 0) {
                    for (i <- 0 until delta) yield {
                      Field(field.key, field.fieldType, Messages("metadata." + field.key.replaceAll(":", ".")), "", field.hasOptions, field.options)
                    }
                  } else {
                    Seq.empty
                  }
                  existing ++ additional
                }

                val files = core.storage.FileStorage.listFiles(itemId).map(f => FileUploadResponse(f))

                val itemViewModel = ItemViewModel(item.itemId, fields, files)
                val itemViewModelAsJson = JJson.generate(itemViewModel)

                Ok(Template("organizations/SimpleDocumentUpload/simpleDocumentUpload.html", 'data -> itemViewModelAsJson, 'uid -> MissingLibs.UUID, 'id -> itemId))

              } catch {
                case t: Throwable =>
                  Error("Could not parse content for item " + itemId, t)
              }

            }.getOrElse {
              Error("Could not find document content for item " + itemId)
            }

          }.getOrElse {
            NotFound(s"Item with ID $itemId was not found")
          }
        }
    }
  }

  def submit = OrganizationMember {
    MultitenantAction {
      implicit request =>
        withAccess {

          val config = SimpleDocumentUploadPlugin.pluginConfiguration

          ItemViewModel.itemForm.bind(request.body.asJson.get).fold(
            formWithErrors => handleValidationError(formWithErrors),
            itemModel => {

              val index: Int = cache.findOne(itemModel.id).map { item =>
                item.index
              }.getOrElse {
                cache.count().toInt
              }

              // handle attached files
              val files: Seq[StoredFile] = FileStorage.listFiles(itemModel.id)

              val (pdfs, images) = files.partition(_.contentType == "application/pdf")

              val pdfUrls = pdfs.map { p =>
                "http://" + request.host + "/file/" + p.id.toString
              }
              val imageUrls = images.map { i =>
                "http://" + request.host + "/image/" + i.id.toString
              }
              val thumbnailUrls = (images ++ pdfs).map { t =>
                "http://" + request.host + "/thumbnail/" + t.id.toString
              }
              val pdfFields = pdfUrls.map { p =>
                """<%s>%s</%s>""".format(
                  IndexField.FULL_TEXT_OBJECT_URL.xmlKey, p, IndexField.FULL_TEXT_OBJECT_URL.xmlKey
                )
              }
              val imageFields = imageUrls.map { i =>
                """<%s>%s</%s>""".format(
                  SystemField.IMAGE_URL.xmlKey, i, SystemField.IMAGE_URL.xmlKey
                )
              }
              val thumbnailFields = thumbnailUrls.map { t =>
                """<%s>%s</%s>""".format(
                  SystemField.THUMBNAIL.xmlKey, t, SystemField.THUMBNAIL.xmlKey
                )
              }
              val fields = itemModel.fields.map { f =>
                """<%s>%s</%s>""".format(f.key, f.value, f.key)
              }

              val titleField: String = itemModel.fields.find(_.key == config.titleField).map(_.value).getOrElse("")

              val automaticFields = Seq(SystemField.TITLE).map { f =>
                """<%s>%s</%s>""".format(f.xmlKey, titleField, f.xmlKey)
              }

              val recordDefinition = RecordDefinition.getRecordDefinition(config.schemaPrefix, config.schemaVersion).getOrElse {
                throw new RuntimeException("This shouldn't logically happen")
              }

              val xml = """<%s:record %s>%s</%s:record>""".format(
                config.schemaPrefix,
                util.XMLUtils.namespacesToString(recordDefinition.allNamespaces),
                (fields ++ automaticFields ++ imageFields ++ thumbnailFields ++ pdfFields).mkString,
                config.schemaPrefix
              )

              val item = MetadataItem(
                collection = config.collectionName,
                itemType = SimpleDocumentUploadPlugin.ITEM_TYPE.itemType,
                itemId = itemModel.id,
                xml = Map(config.schemaPrefix -> xml),
                schemaVersions = Map(config.schemaPrefix -> config.schemaVersion),
                systemFields = Map(
                  SystemField.TITLE.tag -> List(titleField),
                  SystemField.THUMBNAIL.tag -> thumbnailUrls.toList
                ),
                index = index
              )

              cache.saveOrUpdate(item)

              val solrDocument = new mutable.HashMap[String, mutable.Set[Any]] with mutable.MultiMap[String, Any]

              itemModel.fields.foreach { f =>
                val typedFieldKey = "%s_%s".format(f.key.replaceAll(":", "_"), f.fieldType)
                solrDocument.addBinding(typedFieldKey, f.value)
              }
              // TODO hack, solr type should be read from rec-def, then again it is a systemField...
              pdfUrls.foreach { p =>
                solrDocument.addBinding(IndexField.FULL_TEXT_OBJECT_URL.key + "_link", p)
              }
              imageUrls.foreach { i =>
                solrDocument.addBinding(SystemField.IMAGE_URL.tag, i)
              }
              thumbnailUrls.foreach { t =>
                solrDocument.addBinding(SystemField.THUMBNAIL.tag, t)
              }

              solrDocument.addBinding(SystemField.TITLE.tag, titleField)

              solrDocument += (ID -> itemModel.id)
              solrDocument += (HUB_ID -> itemModel.id)
              solrDocument += (ORG_ID -> configuration.orgId)
              solrDocument += (RECORD_TYPE -> SimpleDocumentUploadPlugin.ITEM_TYPE.itemType)

              indexingServiceLocator.byDomain.stageForIndexing(solrDocument.toMap)
              indexingServiceLocator.byDomain.commit

              Json(itemModel)
            })
        }
    }
  }

  def delete(id: String) = OrganizationMember {
    MultitenantAction {
      implicit request =>
        withAccess {
          cache.remove(id)
          indexingServiceLocator.byDomain.deleteById(id)
          FileStorage.deleteFiles(id)
          Ok
        }
    }
  }

  def upload(uid: String, id: String) = OrganizationMember {
    MultitenantAction {
      implicit request =>
        withAccess {
          FileUpload.markFilesAttached(uid, id)
          Ok
        }
    }
  }

  private def withAccess(block: => Result)(implicit request: RequestHeader, configuration: OrganizationConfiguration): Result = {
    if (Group.dao.hasAnyRole(connectedUser, Seq(SimpleDocumentUploadPlugin.ROLE_DOCUMENT_EDITOR, Role.OWN))) {
      block
    } else {
      Forbidden
    }
  }

  private def cache(implicit configuration: OrganizationConfiguration) = {
    val collectionName = SimpleDocumentUploadPlugin.pluginConfiguration.collectionName
    MetadataCache.get(configuration.orgId, collectionName, SimpleDocumentUploadPlugin.ITEM_TYPE)
  }

}

case class ListItemViewModel(id: String, name: String, description: String)

case class ItemViewModel(id: String, fields: Seq[Field], files: Seq[FileUploadResponse] = Seq.empty)

object ItemViewModel {

  val itemForm = Form(
    mapping(
      "id" -> nonEmptyText,
      "fields" -> seq(
        mapping(
          "key" -> nonEmptyText,
          "fieldType" -> nonEmptyText,
          "label" -> nonEmptyText,
          "value" -> text,
          "hasOptions" -> boolean,
          "options" -> seq(text)
        )(Field.apply)(Field.unapply)
      ),
      "files" -> seq(
        mapping(
          "name" -> text,
          "size" -> longNumber,
          "url" -> text,
          "thumbnail_url" -> text,
          "delete_url" -> text,
          "delete_type" -> text,
          "error" -> text,
          "id" -> text
        )(FileUploadResponse.apply)(FileUploadResponse.unapply))
    )(ItemViewModel.apply)(ItemViewModel.unapply)
  )

}

case class Field(key: String, fieldType: String, label: String, value: String, hasOptions: Boolean, options: Seq[String])
