package core

import models.DomainConfiguration

/**
 * Messages for communication with plugins
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
package object messages {

  case class FileStored(bucketId: String, fileIdentifier: String, fileType: Option[String], fileName: String, contentType: String, configuration: DomainConfiguration)

}
