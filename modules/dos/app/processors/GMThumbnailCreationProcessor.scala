/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package processors

import models.dos.Task
import play.api.Play
import play.api.Play.current
import org.apache.commons.io.FileUtils
import controllers.dos._
import java.io._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object GMThumbnailCreationProcessor extends ThumbnailCreationProcessor with ThumbnailSupport {

  protected def createThumbnailsForSize(images: Seq[File], width: Int, task: Task, orgId: String, collectionId: String) {

    val gmCommand = getGMCommand(task) getOrElse (return )
    val gmCommandPath = new File(gmCommand)
    if (!gmCommandPath.exists()) {
      error(task, "Could not find GM executable at '%s'".format(gmCommand))
      return
    }

    val tmpDir = new File(Play.configuration.getString("dos.tmpDir").getOrElse("/tmp"))

    val thumbnailTmpDir = new File(tmpDir, "%s_%s".format(task.pathAsFile.getParentFile.getName, width))
    if (thumbnailTmpDir.exists()) {
      FileUtils.deleteDirectory(thumbnailTmpDir)
    }
    if (!thumbnailTmpDir.mkdir()) {
      error(task, "Could not create temporary templating directory '%s' while processing thumbnails of size '%s' for source directory '%s'".format(thumbnailTmpDir.getAbsolutePath, width, task.path))
      return
    }

    for (image <- images; if (!task.isCancelled)) {
      val params = Map(
        ORIGIN_PATH_FIELD -> image.getAbsolutePath,
        IMAGE_ID_FIELD -> getImageName(image.getName),
        TASK_ID -> task._id,
        ORGANIZATION_IDENTIFIER_FIELD -> orgId,
        COLLECTION_IDENTIFIER_FIELD -> collectionId
      )

      createAndStoreThumbnail(
        image,
        width,
        params,
        getStore(task.orgId),
        thumbnailTmpDir,
        onSuccess = { (size, file) => info(task, "Created thumbnail of size '%s' for image '%s'".format(width, image.getAbsolutePath), Some(image.getAbsolutePath), None) },
        onFailure = { (size, file, reason) => error(task, "Error creating thumbnail for image '%s': %s".format(file.getAbsolutePath, reason, Some(image.getAbsolutePath))) }
      )

      Task.dao(task.orgId).incrementProcessedItems(task, 1)

    }
    FileUtils.deleteDirectory(thumbnailTmpDir)
  }

}