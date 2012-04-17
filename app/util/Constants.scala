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

package util

import eu.delving.metadata.SummaryField

/**
 * Constants, used across several building blocks
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Constants {

  // ~~~ hub types, used in view as itemName and otherwise for links and search recordTypes
  val OBJECT = "object"
  val USERCOLLECTION = "collection"
  val STORY = "story"
  val USER = "user"
  val MDR = "mdr"
  val DATASET ="dataSet"

  // ~~~ identifier fields
  val ID = "id"
  val HUB_ID = "delving_hubId"
  val PMH_ID = "delving_pmhId"
  val ORG_ID = "delving_orgId"
  val EUROPEANA_URI = "europeana_uri"

  val SCHEMA = "delving_currentSchema"
  val PUBLIC_SCHEMAS = "delving_publicSchemas"

  // ~~~ special indexing fields
  val COLLECTIONS = "delving_userCollections"

  // ~~~ SummaryFields

  // This is the smallest set of fields that should be set for one
  // displayable record in the CultureHub. Part of them is set at mapping time
  // and the other part by the hub at processing time

  // mapping-time SummaryFields
  val TITLE = SummaryField.TITLE.tag
  val DESCRIPTION = SummaryField.DESCRIPTION.tag
  val OWNER = SummaryField.OWNER.tag
  val CREATOR = SummaryField.CREATOR.tag
  val THUMBNAIL = SummaryField.THUMBNAIL.tag
  val LANDING_PAGE = SummaryField.LANDING_PAGE.tag
  val DEEP_ZOOM_URL = SummaryField.DEEP_ZOOM_URL.tag
  val PROVIDER = SummaryField.PROVIDER.tag

  // processing-time SummaryFields
  val SPEC = SummaryField.SPEC.tag
  val VISIBILITY = "delving_visibility"
  val RECORD_TYPE = "delving_recordType"
  val MIMETYPE = "delving_mimeType"
  val HAS_DIGITAL_OBJECT = "delving_hasDigitalObject"

  // TODO add to SummaryField??
  val FULL_TEXT_OBJECT_URL = "delving_fullTextObjectUrl"

  // ~~~ link value fields
  val USERCOLLECTION_ID = "userCollectionId" // mongo ID of a collection
  val OBJECT_ID = "objectId"

  // ~~~~~ Solr Constants
  val MORE_LIKE_THIS = "moreLikeThis"

  // ~~~ special cases
  val MDR_LOCAL_ID: String = "localRecordKey"
  val MDR_HUB_ID = "hubId"
  val MDR_HUBCOLLECTION = "hubCollection"

}