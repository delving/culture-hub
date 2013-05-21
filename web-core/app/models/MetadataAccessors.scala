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
import core.HubId
import core.indexing.IndexField._
import core.SystemField._
import org.bson.types.ObjectId
import views.Helpers.thumbnailUrl
import java.net.{ URLEncoder, URLDecoder }

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
abstract class MetadataAccessors {

  protected def assign(key: String): String

  protected def values(key: String): List[String]

  // TODO cleanup, unify, decide, conquer

  // ~~~ identifiers
  def getHubId: String = URLDecoder.decode(assign(HUB_ID.key), "utf-8")

  def getSplitHubId = HubId(getHubId)

  def getOrgId: String = getSplitHubId.orgId
  def getSpec: String = getSplitHubId.spec
  def getRecordId: String = getSplitHubId.localId

  // ~~~ well-known, always provided, meta-data fields
  def getItemType: String = assign(RECORD_TYPE.key)
  def getRecordSchema: String = assign(SCHEMA.key)
  def getTitle: String = assign(TITLE.tag)
  def getDescription: String = assign(DESCRIPTION.tag)
  def getOwner: String = assign(OWNER.tag)
  def getVisibility: String = assign(VISIBILITY.key)

  def getUri(implicit configuration: OrganizationConfiguration): String = "/" + getOrgId + "/" + getSpec + "/" + getRecordId

  def getLandingPage = getItemType match {
    case ITEM_TYPE_MDR => assign(LANDING_PAGE.tag)
    case _ => ""
  }
  def getThumbnailUri(configuration: OrganizationConfiguration): String = getThumbnailUri(180, configuration)

  def getThumbnailUri(size: Int, configuration: OrganizationConfiguration): String = {
    assign(THUMBNAIL.tag) match {
      case id if ObjectId.isValid(id) && !id.trim.isEmpty =>
        val mongoId = Some(new ObjectId(id))
        thumbnailUrl(mongoId, size)
      case url if url.startsWith("http") =>
        if (configuration.objectService.imageCacheEnabled) {
          "/thumbnail/cache?id=%s&width=%s".format(URLEncoder.encode(url, "utf-8"), size)
        } else {
          url
        }
      case _ => thumbnailUrl(None, size)
    }
  }
  def getMimeType: String = "unknown/unknown"

  def hasDigitalObject = assign(THUMBNAIL.tag) match {
    case url if url.trim().length() > 0 => true
    case _ => false
  }

}