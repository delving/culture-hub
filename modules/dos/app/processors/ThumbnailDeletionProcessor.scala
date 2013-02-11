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

import controllers.dos._
import models.dos.Task
import java.io.File
import controllers.dos.Thumbnail
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ThumbnailDeletionProcessor extends Processor with Thumbnail {

  def process(task: Task, processorParams: Map[String, AnyRef]) {

    val store = getStore(task.orgId)

    if (!task.params.contains(controllers.dos.COLLECTION_IDENTIFIER_FIELD) || !task.params.contains(controllers.dos.ORGANIZATION_IDENTIFIER_FIELD)) {
      error(task, "No spec or organisation provided")
    } else {
      info(task, "Starting to delete thumbnails for directory " + task.path)
      val thumbs = store.find(MongoDBObject(ORIGIN_PATH_FIELD -> task.path.r, COLLECTION_IDENTIFIER_FIELD -> task.params(COLLECTION_IDENTIFIER_FIELD).toString, ORGANIZATION_IDENTIFIER_FIELD -> task.params(ORGANIZATION_IDENTIFIER_FIELD).toString))
      Task.dao(task.orgId).setTotalItems(task, thumbs.size)
      thumbs foreach {
        t =>
          {
            val origin = t.get(ORIGIN_PATH_FIELD).toString
            info(task, "Removing thumbnails for image " + origin, Some(origin))
            Task.dao(task.orgId).incrementProcessedItems(task, 1)
            store.remove(t.getId.asInstanceOf[ObjectId])
          }
      }
    }
  }
}