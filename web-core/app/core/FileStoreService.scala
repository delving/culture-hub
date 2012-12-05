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
  def listFiles(bucketId: String, fileType: Option[String])(implicit configuration: DomainConfiguration): List[StoredFile]

  /**
   * Deletes all the files belonging to a bucket
   * @param bucketId the identifier of a bucket
   * @param fileType an optional discriminator for the file
   * @param configuration the running [[ DomainConfiguration ]]
   */
  def deleteFiles(bucketId: String, fileType: Option[String])(implicit configuration: DomainConfiguration)

  /**
   * Deletes a single file
   * @param fileType an optional discriminator for the file
   * @param fileIdentifier the unique identifier of the file
   * @param configuration the running [[ DomainConfiguration ]]
   */
  def deleteFile(bucketId: String, fileType: Option[String], fileIdentifier: String)(implicit configuration: DomainConfiguration)

  /**
   * Stores a single file in the underlying storage
   * @param bucketId the bucket to which this file should be added
   * @param file the File
   * @param contentType the content-type of the file
   * @param fileName the file name this file should be given
   * @param fileType an optional discriminator for the file
   * @return a [[ StoredFile ]] if the storage is successful, None otherwise
   */
  def storeFile(bucketId: String, fileType: Option[String], file: File, contentType: String, fileName: String): Option[StoredFile]

  /**
   * Renames a bucket
   * @param oldBucketId the current identifier of the bucket
   * @param newBucketId the new identifier of the bucket
   */
  def renameBucket(oldBucketId: String, newBucketId: String)

}