package controllers.mediator

import play.api.mvc._
import com.escalatesoft.subcut.inject.BindingModule
import controllers.DelvingController
import plugins.MediatorPlugin
import java.io.{ FilenameFilter, File }
import play.api.libs.iteratee.Enumerator
import models.OrganizationConfiguration

/**
 * Handles various media representations
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class Representations(implicit val bindingModule: BindingModule) extends DelvingController {

  def representation(representationType: String, orgId: String, collection: String, id: String, accessKey: Option[String] = None) = OrganizationConfigured {
    implicit request =>

      representationType match {
        case "image" =>

          if (MediatorPlugin.pluginConfiguration.sourceImageRepresentationAccessKey.isDefined &&
            MediatorPlugin.pluginConfiguration.sourceImageRepresentationAccessKey != accessKey) {
            Unauthorized
          } else {
            findResourceFile(orgId, collection, id) map { resource =>
              val fileContent = Enumerator.fromFile(resource)
              SimpleResult(
                header = ResponseHeader(200, Map(CONTENT_LENGTH -> resource.length.toString)),
                body = fileContent
              )
            } getOrElse {
              NotFound
            }
          }

        case _ => NotFound
      }

  }

  def findResourceFile(orgId: String, collection: String, id: String)(implicit configuration: OrganizationConfiguration): Option[File] = {
    val archiveLocation = new File(MediatorPlugin.pluginConfiguration.archiveDirectory, s"$orgId/$collection")
    archiveLocation.listFiles(new FilenameFilter {
      def accept(dir: File, name: String): Boolean = name.startsWith(id)
    }).headOption
  }

}
