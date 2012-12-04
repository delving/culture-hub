package core

import core.storage.StoredFile
import models.DomainConfiguration
import java.io.File

/**
 * File Store interface.
 *
 * This trait describes the basic operations a FileStoreService needs to provide to be useful in the context of a hub.
 *
 * We use the notion of "bucket" to which a file belongs to. A bucket can e.g. be an item identifier, a collection identifier, etc.
 * A file also has a unique identifier by which it can be retrieved.
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
  def listFiles(bucketId: String)(implicit configuration: DomainConfiguration): List[StoredFile]

  /**
   * Deletes all the files belonging to a bucket
   * @param bucketId the identifier of a bucket
   * @param configuration the running [[ DomainConfiguration ]]
   */
  def deleteFiles(bucketId: String)(implicit configuration: DomainConfiguration)

  /**
   * Deletes a single file
   * @param fileIdentifier the unique identifier of the file
   * @param configuration the running [[ DomainConfiguration ]]
   */
  def deleteFile(bucketId: String, fileIdentifier: String)(implicit configuration: DomainConfiguration)

  /**
   * Stores a single file in the underlying storage
   * @param bucketId the bucket to which this file should be added
   * @param file the File
   * @param contentType the content-type of the file
   * @param fileName the file name this file should be given
   * @return a [[ StoredFile ]] if the storage is successful, None otherwise
   */
  def storeFile(bucketId: String, file: File, contentType: String, fileName: String): Option[StoredFile]

  /**
   * Renames a bucket
   * @param oldBucketId the current identifier of the bucket
   * @param newBucketId the new identifier of the bucket
   */
  def renameBucket(oldBucketId: String, newBucketId: String)

}
