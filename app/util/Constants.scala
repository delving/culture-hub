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

  // ~~~ identifier fields
  val ID = "id"
  val HUB_ID = "delving_hubId"
  val PMH_ID = "delving_pmhId"
  val ORG_ID = "delving_orgId"

  val SPEC = "delving_spec"
  val FORMAT = "delving_currentFormat"
  val RECORD_TYPE = "delving_recordType"

  // ~~~ special indexing fields
  val COLLECTIONS = "delving_userCollections"

  // ~~~ "the guys" for access
  val TITLE = SummaryField.TITLE.tag
  val DESCRIPTION = SummaryField.DESCRIPTION.tag
  val OWNER = SummaryField.OWNER.tag
  val CREATOR = SummaryField.CREATOR.tag
  val VISIBILITY = SummaryField.VISIBILITY.tag
  val THUMBNAIL = SummaryField.THUMBNAIL.tag

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