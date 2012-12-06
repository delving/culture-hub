package core

import core.storage.StoredFile
import models.DomainConfiguration
import java.io.File

/**
 * File Store interface.
 *
 * This trait describes the basic operations a FileStoreService needs to provide to be useful in the context of a hub.
 *
 * We abstract over the traditional notion of file on a file-system and add means to keep the files organized by introducing the following concepts:
 *
 * - bucket: represents a place in which a file is stored. Buckets can be listed, renamed, removed.
 * - fileType: an optional discriminator making it possible to arbitrarily classify files
 * - unique file identifier: each file should have a unique identifier, making it possible to retrieve it via this identifier. The storage service
 *   must ensure that the file identifier be constant over time (even if e.g. a bucket is renamed)
 *
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait FileStoreService {

  /**
   * Lists all files belonging to a bucket
   * @param bucketId the identifier of a bucket
   * @param configuration the running [[ DomainConfiguration ]]
   * @return a sequence of [[ StoredFile ]]
   */
  def listFiles(bucketId: String, fileType: Option[String] = None)(implicit configuration: DomainConfiguration): List[StoredFile]

  /**
   * Deletes all the files belonging to a bucket
   * @param bucketId the identifier of a bucket
   * @param fileType an optional discriminator for the file
   * @param configuration the running [[ DomainConfiguration ]]
   */
  def deleteFiles(bucketId: String, fileType: Option[String] = None)(implicit configuration: DomainConfiguration)

  /**
   * Stores a single file in the underlying storage
   * @param file the File
   * @param contentType the content-type of the file
   * @param fileName the file name this file should be given
   * @param bucketId the bucket to which this file should be added
   * @param fileType an optional discriminator for the file
   * @param params optional meta-data
   * @param advertise whether or not an event should be sent upon storing the file
   * @return a [[ StoredFile ]] if the storage is successful, None otherwise
   */
  def storeFile(file: File, contentType: String, fileName: String, bucketId: String, fileType: Option[String] = None,
                params: Map[String, AnyRef] = Map.empty, advertise: Boolean)(implicit configuration: DomainConfiguration): Option[StoredFile]

  /**
   * Retrieves a single file given its identifier
   * @param fileIdentifier the unique identifier of the file
   * @return a [[ StoredFile ]] if the lookup is successful, None otherwise
   */
  def retrieveFile(fileIdentifier: String)(implicit configuration: DomainConfiguration): Option[StoredFile]

  /**
   * Deletes a single file
   * @param fileIdentifier the unique identifier of the file
   * @param configuration the running [[ DomainConfiguration ]]
   */
  def deleteFile(fileIdentifier: String)(implicit configuration: DomainConfiguration)

  /**
   * Renames a bucket
   * @param oldBucketId the current identifier of the bucket
   * @param newBucketId the new identifier of the bucket
   * @param configuration the running [[ DomainConfiguration ]]
   */
  def renameBucket(oldBucketId: String, newBucketId: String)(implicit configuration: DomainConfiguration)

  /**
   * Changes the fileType of files
   * @param newFileType the new file type, optional
   * @param files the files for which to change the type
   * @param configuration the running [[ DomainConfiguration ]]
   */
  def setFileType(newFileType: Option[String], files: Seq[StoredFile])(implicit configuration: DomainConfiguration)

}