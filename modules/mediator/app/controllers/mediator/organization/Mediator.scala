package controllers.mediator.organization

import controllers.OrganizationController
import play.api.mvc._
import plugins.MediatorPlugin
import java.io.File
import extensions.Email
import models.HubUser
import util.Quotes
import controllers.dos.{ Thumbnail, ThumbnailSupport }
import scala.collection.JavaConverters._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object Mediator extends OrganizationController with ThumbnailSupport {

  def collection(collection: String) = OrganizationAdmin {

    Action { implicit request =>

      def safeList(p: File): Seq[File] = if (p.exists() && p.isDirectory) p.listFiles() else Seq.empty[File]

      // create the FTP directory, if it did not exist
      val collectionSourceDir = new File(MediatorPlugin.pluginConfiguration.sourceDirectory, collection)
      if (!collectionSourceDir.exists()) collectionSourceDir.mkdir()

      val uploadedFiles = collectionSourceDir.listFiles

      // list of thumbnails, deepZoom images, archived images, in error images
      val thumbnails: Seq[Thumbnail] = listThumbnailFiles(configuration.orgId, collection)

      val tiles: Seq[File] = {
        val p = new File(configuration.objectService.tilesOutputBaseDir, s"/${configuration.orgId}/$collection")
        safeList(p)
      }

      val archivedSourceFiles: Seq[File] = {
        val archive = new File(MediatorPlugin.pluginConfiguration.archiveDirectory, collection)
        safeList(archive)
      }

      val thumbnailMap = thumbnails.map(t => (imageName(t.fileName) -> t)).toMap
      val tileMap = tiles.map(t => (imageName(t.getName) -> t)).toMap
      val archiveMap = archivedSourceFiles.map(s => (imageName(s.getName) -> s)).toMap

      // the thumbnails are the reference, for backwards-compatibility
      val groupedDisplayResult = (thumbnailMap map { pair =>
        {
          val thumb = pair._2
          val maybeTile = tileMap.get(pair._1)
          val maybeArchiveFile = archiveMap.get(pair._1)

          // convenient output to list this with groovy
          Map(
            "thumbnailUrl" -> s"/thumbnail/${configuration.orgId}/$collection/${imageName(thumb.fileName)}",
            "fileName" -> thumb.fileName,
            "thumbnailWidths" -> {
              thumb.widths.asJava
            },
            "tileName" -> (maybeTile.map(_.getName).getOrElse("")),
            "tileUrl" -> {
              maybeTile map { t => MediatorPlugin.pluginConfiguration.mediaServerUrl + s"/deepzoom/${configuration.orgId}/$collection/${t.getName}" } getOrElse { "" }
            },
            "hasSourceFile" -> maybeArchiveFile.isDefined
          ).asJava
        }
      }).asJava

      Ok(Template('items -> groupedDisplayResult))
    }
  }

  def newFileFault(orgId: String, set: String, fileName: String, userName: String) = OrganizationConfigured {
    Action { implicit request =>
      val error = request.body.asText

      log.debug(s"[$userName@$orgId] Received file handling response from media server for $set/$fileName, ${if (error.isDefined) "with error: " + error.get}")

      error.map { message =>

        val content: String = s"""
                  |Master,
                  |
                  |there was a problem while processing the file '$fileName' that you have uploaded to the Mediator-managed FTP of organization $orgId.
                  |
                  |The error is:
                  |
                  |$message
                  |
                  |
                  |Yours truly,
                  |
                  |The Mediator
                  |
                  |----
                  |${Quotes.randomQuote()}
                """.stripMargin

        HubUser.dao.findByUsername(userName).map { user =>
          Email(configuration.emailTarget.systemFrom, s"[Mediator] Error while processing file '$fileName'").
            to(user.email).
            withContent(content).
            send()
        }.getOrElse {
          log.error(s"Could not find user $userName, the content of the mail would have been:\n\n" + content)
        }
      }

      Ok
    }
  }

}
