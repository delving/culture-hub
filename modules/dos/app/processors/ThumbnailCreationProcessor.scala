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
import java.io.File

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait ThumbnailCreationProcessor extends Processor {

  def process(task: Task, processorParams: Map[String, AnyRef]) {
    val p = new File(task.path)
    val collectionId = task.params.get(controllers.dos.COLLECTION_IDENTIFIER_FIELD).getOrElse({
      error(task, "No spec passed for task " + task)
      return
    })
    val orgId = task.params.get(controllers.dos.ORGANIZATION_IDENTIFIER_FIELD).getOrElse({
      error(task, "No orgId passed for task " + task)
      return
    })

    val sizes = processorParams("sizes").asInstanceOf[List[Int]]

    info(task, s"Starting to generate thumbnails for path '${task.path}' for sizes ${sizes.mkString(", ")}, parameters: ${parameterList(task)}")

    val images = p.listFiles().filter(f => isImage(f.getName))

    Task.dao(task.orgId).setTotalItems(task, images.size * sizes.length)

    for (s <- sizes; if (!task.isCancelled)) createThumbnailsForSize(images, s, task, orgId, collectionId)
  }

  protected def createThumbnailsForSize(images: Seq[File], width: Int, task: Task, orgId: String, collectionId: String)

}