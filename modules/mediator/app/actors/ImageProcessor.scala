package actors

import akka.actor.Actor
import java.io.File
import libs.{ PTIFTiling, Normalizer }
import models.OrganizationConfiguration
import org.apache.commons.io.FileUtils
import play.api.{ Play, Logger }
import controllers.dos._
import controllers.dos.fileStore
import scala.collection.mutable.ArrayBuffer
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current
import plugins.MediatorPlugin

/**
 * Actor that handles the pipeline for media ingestion for a single image
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class ImageProcessor extends Actor with ThumbnailSupport {

  val log = Logger("CultureHub")

  val operations: Seq[ProcessImage => Option[String]] = Seq(makeThumbnails, makeDeepZoom)

  def receive = {

    case context @ ProcessImage(orgId, set, file, userName, errorCallbackUrl, configuration) =>

      val errors: Seq[String] = operations flatMap { op =>
        op(context)
      }

      if (errors.isEmpty) {
        val destinationDir = new File(MediatorPlugin.pluginConfiguration(configuration).archiveDirectory, s"/$orgId/$set")
        // move the original file to archive
        val maybeArchived = new File(destinationDir, file.getName)
        if (maybeArchived.exists()) maybeArchived.delete()
        FileUtils.moveFileToDirectory(file, destinationDir, true)
      } else {
        log.debug(s"[MediatorServer] [$userName@$orgId] Errors during creation of alternative representations of file $set/$file: we're reporting back to $errorCallbackUrl")
        val params = Seq(
          "orgId" -> orgId,
          "set" -> set,
          "fileName" -> file.getName,
          "userName" -> userName,
          "error" -> errors.mkString("\n")
        )
        WS
          .url(errorCallbackUrl)
          .withQueryString(params: _*)
          .post(errors.mkString("\n")).map { result => log.debug("Mediator: Result of error callback operation: " + result.ahcResponse.getStatusCode) }

      }
  }

  def makeThumbnails(context: ProcessImage): Option[String] = {
    val params = Map(
      ORIGIN_PATH_FIELD -> context.file.getAbsolutePath,
      IMAGE_ID_FIELD -> imageName(context.file.getName),
      ORGANIZATION_IDENTIFIER_FIELD -> context.orgId,
      COLLECTION_IDENTIFIER_FIELD -> context.set
    )

    val tmpDir = new File(context.configuration.objectService.tilesWorkingBaseDir + File.separator + "thumbnailTmp" + File.separator + context.orgId + context.set)
    tmpDir.mkdirs()

    val errors = new ArrayBuffer[String]

    thumbnailSizes.map { size =>
      createAndStoreThumbnail(
        context.file,
        size._2,
        params,
        fileStore(context.configuration),
        tmpDir,
        { (width, file) => },
        { (width, file, reason) =>
          log.warn(s"Could not create thumbnail of width $width for file ${file.getName}: $reason")
          errors += reason
        }
      )(context.configuration)
    }

    if (!errors.isEmpty) {
      Some(errors.mkString(","))
    } else {
      None
    }

  }

  def makeDeepZoom(context: ProcessImage): Option[String] = {
    val tilesOutputDir = new File(context.configuration.objectService.tilesOutputBaseDir, context.orgId + "/" + context.set)
    tilesOutputDir.mkdirs()
    val tilesWorkingDir = new File(context.configuration.objectService.tilesWorkingBaseDir)
    val normalizationWorkingDir = tilesWorkingDir.getAbsolutePath + File.separator + "normalized"
    new File(normalizationWorkingDir).mkdirs()

    val sourceFile = new File(normalizationWorkingDir + File.separator + context.file.getName)
    FileUtils.copyFile(context.file, sourceFile)

    log.info("%s: normalizing image for file %s".format(context.configuration.orgId, context.file.getName))
    val tileSource = Normalizer.normalize(sourceFile, new File(normalizationWorkingDir)).getOrElse(sourceFile)

    try {
      log.info("%s: creating tiles for file %s".format(context.configuration.orgId, context.file.getName))
      val result = PTIFTiling.createTile(tilesWorkingDir, tilesOutputDir, tileSource)
      result.left.toOption
    } finally {
      FileUtils.deleteQuietly(sourceFile)
      FileUtils.deleteQuietly(tileSource)
    }
  }

}

case class ProcessImage(orgId: String, set: String, file: File, userName: String, errorCallbackUrl: String, configuration: OrganizationConfiguration)
