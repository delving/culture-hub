package plugins

import _root_.libs.{ PTIFTiling, Normalizer }
import play.api._
import core.CultureHubPlugin
import core.storage.FileStorage
import models.OrganizationConfiguration
import controllers.dos.ThumbnailSupport
import models.HubMongoContext._
import core.messages.FileStored
import java.io.File

class DeepZoomPlugin(app: Application) extends CultureHubPlugin(app) with ThumbnailSupport {

  val pluginKey: String = "deepZoom"

  override def receive = {

    case FileStored(bucketId, fileIdentifier, fileType, fileName, contentType, configuration: OrganizationConfiguration) =>

      if (contentType.contains("image")) {
        debug("Image stored: " + fileName)

        FileStorage.retrieveFile(fileIdentifier)(configuration).map { file =>

          val tilesOutputDir = new File(configuration.objectService.tilesOutputBaseDir)
          val tilesWorkingDir = new File(configuration.objectService.tilesWorkingBaseDir)
          val normalizationWorkingDir = tilesWorkingDir.getAbsolutePath + File.pathSeparator + "normalized"
          new File(normalizationWorkingDir).mkdirs()

          val sourceFile = new File(normalizationWorkingDir + File.separator + fileName)
          file.writeTo(sourceFile)

          info("%s: normalizing image for file %s".format(configuration.orgId, fileName))
          val tileSource = Normalizer.normalize(sourceFile, new File(normalizationWorkingDir)).getOrElse(sourceFile)

          info("%s: creating tiles for file %s".format(configuration.orgId, fileName))
          PTIFTiling.createTile(tilesWorkingDir, tilesOutputDir, tileSource)
        }
      }

  }
}
