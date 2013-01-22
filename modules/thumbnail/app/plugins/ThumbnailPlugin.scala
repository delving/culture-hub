package plugins

import play.api._
import core.CultureHubPlugin
import core.storage.FileStorage
import models.OrganizationConfiguration
import controllers.dos.Thumbnail
import models.HubMongoContext._
import core.messages.FileStored

class ThumbnailPlugin(app: Application) extends CultureHubPlugin(app) with Thumbnail {

  val pluginKey: String = "thumbnail"

  override def receive = {

    case FileStored(bucketId, fileIdentifier, fileType, fileName, contentType, configuration: OrganizationConfiguration) =>

      if (contentType.contains("image")) {
        debug("File stored: " + fileName)

        FileStorage.retrieveFile(fileIdentifier)(configuration).map { file =>
          info("%s: Creating thumbnail for file %s".format(configuration.orgId, fileName))
          createThumbnailsFromStream(file.content, fileIdentifier, file.name, file.contentType, fileStore(configuration))
        }
      }


  }
}
