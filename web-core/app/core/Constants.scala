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
  val DATASET = "dataSet"


  // TODO cleanup
  val MIMETYPE = "delving_mimeType"

  // TODO remove, in view, use resolver mechanism using the hub_id and splitting it. adjust in Musip
  val HUB_URI = "delving_hubUrl"

  // TODO must become part of the recDef
  val FULL_TEXT_OBJECT_URL = "delving_fullTextObjectUrl"


  // TODO turn into enum-like type
  val ITEM_TYPE_MDR = "mdr"

  // ~~~~~ Solr Constants
  val MORE_LIKE_THIS = "moreLikeThis"

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