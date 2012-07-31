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

import org.bson.types.ObjectId
import views.Helpers.getThumbnailUrl
import models.{DomainConfiguration, Visibility, ViewableItem, DataSetState}

// ~~ short models, mainly for browsing & displaying things view full rendering

case class ShortDataSet(id: Option[ObjectId] = None,
                        spec: String = "",
                        total_records: Long = 0,
                        state: DataSetState = DataSetState.INCOMPLETE,
                        errorMessage: Option[String],
                        facts: Map[String, String] = Map.empty[String, String],
                        recordDefinitions: List[String] = List.empty[String],
                        indexingMappingPrefix: String,
                        orgId: String,
                        userName: String,
                        lockedBy: Option[String],
                        errors: Map[String, String] = Map.empty[String, String],
                        visibility: Int = 0) {

  val error: String = errorMessage.map {
    m => m.replaceAll("\n", "<br/>")
  }.getOrElse("")
}

case class Fact(name: String, prompt: String, value: String)

case class Token(id: String,
                 name: String,
                 tokenType: Option[String] = None,
                 data: Option[Map[String, String]] = None)

case class ListItem(id: String,
                    itemType: String,
                    title: String,
                    description: String = "",
                    thumbnailUrl: String = "",
                    mimeType: String = "unknown/unknown",
                    userName: String,
                    isPrivate: Boolean,
                    url: String) extends ViewableItem {

  def getHubId = "%s_%s_%s".format(userName, itemType, id)
  def getItemType = itemType
  def getTitle = title
  def getDescription = description
  def getOwner = userName
  def getCreator = userName
  def getVisibility = if(isPrivate) Visibility.PRIVATE.value.toString else Visibility.PUBLIC.value.toString
  def getUri = url
  def getLandingPage = url
  def getThumbnailUri = thumbnailUrl
  def getThumbnailUri(size: Int) = thumbnailUrl
  def getMimeType = mimeType
  def hasDigitalObject = !thumbnailUrl.trim.isEmpty
}

abstract class ViewModel {
  val errors: Map[String, String]
}
