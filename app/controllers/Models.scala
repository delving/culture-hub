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
import org.bson.types.ObjectId
import models._
import views.context.getThumbnailUrl

// ~~ short models, mainly for browsing & displaying things view full rendering

case class ShortDataSet(id: Option[ObjectId] = None,
                        spec: String = "",
                        total_records: Int = 0,
                        state: DataSetState = DataSetState.INCOMPLETE,
                        facts: Map[String, String] = Map.empty[String, String],
                        recordDefinitions: List[String] = List.empty[String],
                        indexingMappingPrefix: String,
                        orgId: String,
                        userName: String,
                        lockedBy: Option[ObjectId],
                        errors: Map[String, String] = Map.empty[String, String], visibility: Int = 0)

case class Fact(name: String, prompt: String, value: String)

case class ShortLabel(labelType: String, value: String)

case class Token(id: String, name: String, tokenType: Option[String] = None, data: Option[Map[String, String]] = None)

case class ListItem(id: String,
                    recordType: String,
                    title: String,
                    description: String = "",
                    thumbnailId: Option[ObjectId] = None,
                    thumbnailUrl: Option[String] = None,
                    userName: String,
                    isPrivate: Boolean,
                    url: String) extends Universal {
  
  def thumbnail(size: Int = 100): String = (thumbnailId, thumbnailUrl) match {
    case (None,  None) => getThumbnailUrl(None)
    case (Some(id), None) => getThumbnailUrl(Some(id), size)
    case (None, Some(url)) => url
  }

  def getMongoId = id
  def getHubId = "%s_%s_%s".format(userName, recordType, id)
  def getOwnerId = userName
  def getRecordType = recordType
  def getTitle = title
  def getDescription = description
  def getOwner = userName
  def getCreator = userName
  def getVisibility = if(isPrivate) Visibility.PRIVATE.value.toString else Visibility.PUBLIC.value.toString
  def getUri = url
  def getLandingPage = url
  def getThumbnailUri = thumbnail(100)
  def getThumbnailUri(size: Int) = thumbnail(size)
  def getMimeType = "unknown/unknown"
  def hasDigitalObject = thumbnailId != None || thumbnailUrl != None
}

case class ShortObjectModel(id: String, url: String, thumbnail: String, title: String, hubType: String, files: Seq[StoredFile] = Seq.empty[StoredFile])

// ~~ reference objects

case class CollectionReference(id: String, name: String)

abstract class ViewModel {
  val errors: Map[String, String]
  lazy val validationRules: Map[String, String] = util.Validation.getClientSideValidationRules(this.getClass)
}
