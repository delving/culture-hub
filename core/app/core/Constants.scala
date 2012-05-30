package core

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

/**
 * Constants, used across several building blocks
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Constants {

  // ~~~ hub types, used in view as itemName and otherwise for links and search recordTypes
  val OBJECT = "object"
  val USERCOLLECTION = "userCollection"
  val STORY = "story"
  val USER = "user"
  val DATASET = "dataSet"

  // ~~~ identifier fields
  val ID = "id"
  val HUB_ID = "delving_hubId"
  val PMH_ID = "delving_pmhId"
  val ORG_ID = "delving_orgId"
  val EUROPEANA_URI = "europeana_uri"


  // ~~~ housekeeping fields
  val SCHEMA = "delving_currentSchema"
  val PUBLIC_SCHEMAS = "delving_publicSchemas"
  val ALL_SCHEMAS = "delving_allSchemas"

  val SYSTEM_TYPE = "delving_systemType"

  // ~~~ system types
  val INDEX_API_ITEM = "indexApiItem"
  val HUB_ITEM = "hubItem"


  // ~~~ special indexing fields
  val COLLECTIONS = "delving_userCollections"

  // ~~~ SystemFields

  // This is the smallest set of fields that should be set for one
  // displayable record in the CultureHub. Part of them is set at mapping time
  // and the other part by the hub at processing time

  // mapping-time SystemFields
  val TITLE = SystemField.TITLE.tag
  val DESCRIPTION = SystemField.DESCRIPTION.tag
  val THUMBNAIL = SystemField.THUMBNAIL.tag
  val EXTERNAL_LANDING_PAGE = SystemField.LANDING_PAGE.tag

  val OWNER = SystemField.OWNER.tag
  val CREATOR = SystemField.CREATOR.tag
  val DEEP_ZOOM_URL = SystemField.DEEP_ZOOM_URL.tag
  val PROVIDER = SystemField.PROVIDER.tag

  // processing-time SystemFields
  val SPEC = SystemField.SPEC.tag
  val VISIBILITY = "delving_visibility"
  val MIMETYPE = "delving_mimeType"
  val HAS_DIGITAL_OBJECT = "delving_hasDigitalObject"

  val HUB_URI = "delving_hubUrl"

  val FULL_TEXT_OBJECT_URL = "delving_fullTextObjectUrl"


  // TODO harmonize the following
  val RECORD_TYPE = "delving_recordType"
  val ITEM_TYPE_MDR = "mdr"
  val MDR = "mdr"
  val ITEM_TYPE_INDEX = INDEX_API_ITEM


  // ~~~ link value fields
  val USERCOLLECTION_ID = "userCollectionId" // mongo ID of a collection
  val OBJECT_ID = "objectId"

  // ~~~~~ Solr Constants
  val MORE_LIKE_THIS = "moreLikeThis"

  // ~~~ special cases
  val MDR_LOCAL_ID: String = "localRecordKey"
  val MDR_HUB_ID = "hubId"
  val MDR_HUBCOLLECTION = "hubCollection"


  // TODO move to conf?
  val PAGE_SIZE: Int = 12

  // ~~~ ACCESS CONTROL

  val USERNAME = "userName"
  val ORGANIZATIONS = "organizations"
  val GROUPS = "groups"


  // ~~~ MetadataItem types

  val HubId = """^(.*?)_(.*?)_(.*)$""".r


  // ~~~ Search UI
  val RETURN_TO_RESULTS = "returnToResults"
  val SEARCH_TERM = "searchTerm"
  val IN_ORGANIZATION = "inOrg"



}