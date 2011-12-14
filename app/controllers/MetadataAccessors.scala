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

import util.Constants._
import views.context._
import org.bson.types.ObjectId

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
abstract class MetadataAccessors {

  protected def assign(key: String): String

  // TODO cleanup, unify, decide, conquer

  // ~~~ identifiers
  def getMongoId: String = assign(ID)
  def getHubId : String = assign(HUB_ID)

  def getOwnerId: String = getRecordType match {
    case OBJECT | USERCOLLECTION | STORY => getOwner
    case MDR => getOrgId
    case _ => ""
  }

  def getDelvingId : String = assign(PMH_ID)

  // ~~~ institutional record IDs
  def getOrgId : String = if(getHubId != null && getHubId.split("_").length == 3) getHubId.split("_")(0) else ""
  def getSpec : String = if(getHubId != null && getHubId.split("_").length == 3) getHubId.split("_")(1) else ""
  def getRecordId : String = if(getHubId != null && getHubId.split("_").length == 3) getHubId.split("_")(2) else ""

  // ~~~ well-known, always provided, meta-data fields
  def getRecordType: String = assign(RECORD_TYPE)
  def getTitle : String = assign(TITLE)
  def getDescription: String = assign(DESCRIPTION)
  def getOwner: String = assign(OWNER)
  def getCreator: String = assign(CREATOR)
  def getVisibility: String = assign(VISIBILITY)
  def getUri : String = getRecordType match {
    case OBJECT | USERCOLLECTION | STORY => "/" + getHubId.replaceAll("_", "/")
    case MDR => "/" + getOrgId + "/object/" + getSpec + "/" + getRecordId
    case _ => ""
  }
  def getThumbnailUri: String = getThumbnailUri(180)
  def getThumbnailUri(size: Int): String = {
    getRecordType match {
      case OBJECT | USERCOLLECTION | STORY => assign(THUMBNAIL) match {
          case id if ObjectId.isValid(id) && !id.trim.isEmpty =>
            val mongoId = Some(new ObjectId(id))
            thumbnailUrl(mongoId, size)
          case _ => thumbnailUrl(None, size)
        }
      // TODO plug-in the image cache here for MDRs
      case MDR => assign(THUMBNAIL) match {
        case url if url.trim.length() > 0 => url
        case _ => thumbnailUrl(None, size)
      }
      case _ => thumbnailUrl(None, size)
    }
  }
  def getMimeType: String = "unknown/unknown"

  // ~~~ old and others
  //  def getCreator : String = assign("dc_creator")
  def getYear : String = assign("europeana_year")
  def getProvider : String = assign("europeana_provider")
  def getDataProvider : String = assign("europeana_dataProvider")
  def getLanguage : String = assign("europeana_language")

}




















