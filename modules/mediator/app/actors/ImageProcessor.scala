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
import play.api.mvc.Results
import play.api.Play.current
import plugins.MediatorPlugin

/**
 * Actor that handles the pipeline for media ingestion for a single image
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class ImageProcessor extends Actor with Thumbnail {

  val log = Logger("CultureHub")

  val operations: Seq[ProcessImage => Option[String]] = Seq(makeThumbnails, makeDeepZoom)

  def receive = {

    case context @ ProcessImage(orgId, set, file, callbackUrl, configuration) =>

      val errors: Seq[String] = operations flatMap { op =>
        op(context)
      }

      if (errors.isEmpty) {
        val destinationDir = new File(MediatorPlugin.pluginConfiguration(configuration).archiveDirectory, s"/$orgId/$set")
        // move the original file to archive
        FileUtils.moveFileToDirectory(file, destinationDir, true)
      } else {
        val params = Seq("orgId" -> orgId, "set" -> set, "fileName" -> file.getName, "errors" -> errors.mkString("\n"))
        WS
          .url(callbackUrl)
          .withQueryString(params: _*)
          .post(Results.EmptyContent()).map { result => log.debug("Mediator: Result of error callback operation: " + result.ahcResponse.getStatusCode) }

      }
  }

  def makeThumbnails(context: ProcessImage): Option[String] = {
    val params = Map(
      ORIGIN_PATH_FIELD -> context.file.getAbsolutePath,
      IMAGE_ID_FIELD -> imageName(context.file.getName),
      ORGANIZATION_IDENTIFIER_FIELD -> context.orgId,
      COLLECTION_IDENTIFIER_FIELD -> context.set
    )

    // TODO consolidate all places using GM, move this to the config!!
    val gmCommand = Play.configuration.getString("dos.graphicsmagic.cmd").getOrElse("")

    val tmpDir = new File(context.configuration.objectService.tilesWorkingBaseDir + File.separator + "thumbnailTmp" + File.separator + context.orgId + context.set)
    tmpDir.mkdirs()

    val errors = new ArrayBuffer[String]

    thumbnailSizes.map { size =>
      createAndStoreThumbnail(
        context.file,
        size._2,
        params,
        gmCommand,
        fileStore(context.configuration),
        tmpDir,
        { (width, file) => },
        { (width, file, reason) =>
          log.warn(s"Could not create thumbnail of width $width for file ${file.getName}: $reason")
          errors += reason
        }
      )
    }

    if (!errors.isEmpty) {
      Some(errors.mkString(","))
    } else {
      None
    }

  }

  def makeDeepZoom(context: ProcessImage): Option[String] = {
    val tilesOutputDir = new File(context.configuration.objectService.tilesOutputBaseDir)
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
      FileUtils.deleteQuietly(tileSource)
    }
  }

}

case class ProcessImage(orgId: String, set: String, file: File, callbackUrl: String, configuration: OrganizationConfiguration)
