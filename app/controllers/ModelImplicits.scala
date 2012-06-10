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

package controllers

import dos.StoredFile
import core.Constants._
import models._

/**
 * Implicits for conversion between backend models and view models
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait ModelImplicits extends CoreImplicits {

  // ~~~ View models
  case class ShortObjectModel(id: String, url: String, thumbnail: String, title: String, hubType: String, files: Seq[StoredFile] = Seq.empty[StoredFile], mimeType: String = "unknown/unknown")

  implicit def mdrAccessorToShortObjectModel[T <: MetadataAccessors](record: T) = ShortObjectModel(id = record.getHubId, url = record.getUri, thumbnail = record.getThumbnailUri(500), title = record.getTitle, hubType = MDR)
  implicit def mdrAccessorListToSOMList[T <: MetadataAccessors](records: List[T]) = records.map(mdrAccessorToShortObjectModel(_))
}