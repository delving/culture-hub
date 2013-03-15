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

package controllers {

  import models.HubMongoContext

  package object dos extends HubMongoContext {

    val emptyThumbnailPath = "/public/images/dummy-object.png"
    val emptyThumbnailUrl = "/assets/dos/images/dummy-object.png"

    val DEFAULT_THUMBNAIL_WIDTH = 220
    val thumbnailSizes = Map("tiny" -> 80, "thumbnail" -> 100, "smaller" -> 180, "small" -> 220, "story" -> 350, "big" -> 500)

    val THUMBNAIL_WIDTH_FIELD = "thumbnail_width"

    val ORGANIZATION_IDENTIFIER_FIELD = "orgId"
    val COLLECTION_IDENTIFIER_FIELD = "collectionId"

    val TASK_ID = "task_id" // mongo id of the task that led to the creation of this thing

  }

}