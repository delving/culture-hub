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

import org.bson.types.ObjectId
import controllers.dos._
import java.io.{ FileInputStream, File }
import models.dos.Task

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object JavaThumbnailCreationProcessor extends ThumbnailCreationProcessor with ThumbnailSupport {

  protected def createThumbnailsForSize(images: Seq[File], width: Int, task: Task, orgId: String, collectionId: String) {
    for (image <- images; if (!task.isCancelled)) {
      try {
        val id = createThumbnailFromFile(image, width, task._id, orgId, collectionId)
        info(task, "Created thumbnail of size '%s' for image '%s'".format(width, image.getAbsolutePath), Some(image.getAbsolutePath), Some(id.toString))
      } catch {
        case t: Throwable => error(task, "Error creating thumbnail for image '%s': %s".format(image.getAbsolutePath, t.getMessage), Some(image.getAbsolutePath))
      } finally {
        Task.dao(task.orgId).incrementProcessedItems(task, 1)
      }
    }
  }

  protected def createThumbnailFromFile(image: File, width: Int, taskId: ObjectId, orgId: String, collectionId: String): ObjectId = {
    val imageName = getImageName(image.getName)
    createThumbnailFromStream(new FileInputStream(image), image.getName, "image/jpeg", width, getStore(orgId), Map(
      ORIGIN_PATH_FIELD -> image.getAbsolutePath,
      IMAGE_ID_FIELD -> imageName,
      TASK_ID -> taskId,
      ORGANIZATION_IDENTIFIER_FIELD -> orgId,
      COLLECTION_IDENTIFIER_FIELD -> collectionId
    ))._2
  }

}