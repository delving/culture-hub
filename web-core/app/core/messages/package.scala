package core

import models.OrganizationConfiguration

/**
 * Messages for communication with plugins
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
package object messages {

  case class CollectionCreated(collectionId: String, configuration: OrganizationConfiguration)

  case class CollectionRenamed(oldCollectionId: String, newCollectionId: String, configuration: OrganizationConfiguration)

  case class FileStored(bucketId: String, fileIdentifier: String, fileType: Option[String], fileName: String, contentType: String, configuration: OrganizationConfiguration)

}
