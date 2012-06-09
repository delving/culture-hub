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

package models

import core.Constants._
import org.bson.types.ObjectId
import views.Helpers.thumbnailUrl
import play.api.Play
import play.api.Play.current
import java.net.{URLEncoder, URLDecoder}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
abstract class MetadataAccessors extends Universal {

  protected def assign(key: String): String

  protected def values(key: String): List[String]

  // TODO cleanup, unify, decide, conquer

  // ~~~ identifiers
  def getMongoId: String = assign(ID)
  def getHubId : String = URLDecoder.decode(assign(HUB_ID), "utf-8")

  def getOwnerId: String = getRecordType match {
    case MDR => getOrgId
    case _ => ""
  }

  // ~~~ institutional record IDs
  def getOrgId : String = if(getHubId != null && getHubId.split("_").length == 3) getHubId.split("_")(0) else ""
  def getSpec : String = if(getHubId != null && getHubId.split("_").length == 3) getHubId.split("_")(1) else ""
  def getRecordId : String = if(getHubId != null && getHubId.split("_").length == 3) getHubId.split("_")(2) else ""

  // ~~~ well-known, always provided, meta-data fields
  def getRecordType: String = assign(RECORD_TYPE)
  def getRecordSchema: String = assign(SCHEMA)
  def getTitle : String = assign(TITLE)
  def getDescription: String = assign(DESCRIPTION)
  def getOwner: String = assign(OWNER)
  def getCreator: String = assign(CREATOR)
  def getVisibility: String = assign(VISIBILITY)

  // TODO add plugin mechanism
  def getUri : String = getRecordType match {
    case MDR =>
      // only provide a link if there's something to show via AFF
      val allSchemas = values(ALL_SCHEMAS)
      if(allSchemas.size > 0 && (allSchemas.contains("aff") || allSchemas.contains("icn"))) {
        "/" + getOrgId + "/thing/" + getSpec + "/" + getRecordId
      } else {
        ""
      }

    case _ => assign(HUB_URI)
  }
  def getLandingPage = getRecordType match {
    case MDR => assign(EXTERNAL_LANDING_PAGE)
    case _ => ""
  }
  def getThumbnailUri: String = getThumbnailUri(180)

  def getThumbnailUri(size: Int): String = {
    assign(THUMBNAIL) match {
      case id if ObjectId.isValid(id) && !id.trim.isEmpty =>
        val mongoId = Some(new ObjectId(id))
        thumbnailUrl(mongoId, size)
      case url if url.startsWith("http") =>
        if(Play.configuration.getBoolean("dos.imageCache.enabled").getOrElse(false)) {
          "/thumbnail/cache?id=%s&width=%s".format(URLEncoder.encode(url, "utf-8"), size)
        } else {
          url
        }
      case _ => thumbnailUrl(None, size)
    }
  }
  def getMimeType: String = assign(MIMETYPE) match {
    case t if t.trim().length() > 0 => t
    case _ => "unknown/unknown"
  }

  def hasDigitalObject = assign(THUMBNAIL) match {
    case url if url.trim().length() > 0 => true
    case _ => false
  }

  // ~~~ old and others
  //  def getCreator : String = assign("dc_creator")

  def getYear : String = assign("europeana_year")
  def getProvider : String = assign("europeana_provider")
  def getDataProvider : String = assign("europeana_dataProvider")
  def getLanguage : String = assign("europeana_language")

}




















