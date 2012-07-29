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
abstract class MetadataAccessors extends ViewableItem {

  protected def assign(key: String): String

  protected def values(key: String): List[String]

  // TODO cleanup, unify, decide, conquer

  // ~~~ identifiers
  def getHubId : String = URLDecoder.decode(assign(HUB_ID), "utf-8")

  def getSplitHubId = {
    val HubId(orgId, spec, localRecordKey) = getHubId
    (orgId, spec, localRecordKey)
  }

  def getOrgId : String =  getSplitHubId._1
  def getSpec : String = getSplitHubId._2
  def getRecordId : String = getSplitHubId._3

  // ~~~ well-known, always provided, meta-data fields
  def getItemType: String = assign(RECORD_TYPE)
  def getRecordSchema: String = assign(SCHEMA)
  def getTitle : String = assign(TITLE)
  def getDescription: String = assign(DESCRIPTION)
  def getOwner: String = assign(OWNER)
  def getVisibility: String = assign(VISIBILITY)

  // TODO add plugin mechanism
  def getUri(configuration: DomainConfiguration): String = getItemType match {
    case MDR =>
      // TODO don't use heuristics
      val allSchemas = values(ALL_SCHEMAS)
      val allSupportedFormats = RecordDefinition.enabledDefinitions(configuration)
      val renderFormat = allSupportedFormats.intersect(allSchemas).headOption
      if(renderFormat.isDefined) {
        "/" + getOrgId + "/thing/" + getSpec + "/" + getRecordId
      } else {
        ""
      }

    case _ => assign(HUB_URI)
  }
  def getLandingPage = getItemType match {
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

}




















