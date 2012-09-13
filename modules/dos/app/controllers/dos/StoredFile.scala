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

package controllers.dos

import org.bson.types.ObjectId

/**
 * A File Stored by the FileStore
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
case class StoredFile(id: ObjectId, name: String, contentType: String, length: Long, thumbnail: Option[ObjectId]) {
  def thumbnailUrl = thumbnail match {
    case Some(fid) => "/thumbnail/" + id.toString
    case None => ""
  }

  def asFileUploadResponse(isSelected: ObjectId => Boolean) = FileUploadResponse(name = name, size = length, url = "/file/" + id, thumbnail_url = thumbnailUrl + "/80", delete_url = "/file/" + id, selected = isSelected(id), id = id.toString)

}

case class FileUploadResponse(name: String, size: Long, url: String = "", thumbnail_url: String = "", delete_url: String = "", delete_type: String = "DELETE", error: String = "", selected: Boolean = false, id: String = "")
