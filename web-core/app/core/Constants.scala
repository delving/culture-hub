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

  // TODO turn into enum-like type
  val ITEM_TYPE_MDR = "mdr"

  // ~~~ ACCESS CONTROL
  val USERNAME = "userName"

  // ~~~ Search UI
  val RETURN_TO_RESULTS = "returnToResults"
  val SEARCH_TERM = "searchTerm"
  val IN_ORGANIZATION = "inOrg"

}

case class ItemType(itemType: String)

case class HubId(orgId: String, spec: String, localId: String) {

  val id = "%s_%s_%s".format(orgId, spec, localId)

  val pmhId = "oai:%s_%s:%s".format(orgId, spec, localId)

  override def toString: String = id
}

object HubId {

  val HubIdExtractor = """^(.*?)_(.*?)_(.*)$""".r

  def apply(id: String): HubId = {
    val HubIdExtractor(orgId, spec, localId) = id
    HubId(orgId, spec, localId)
  }

}
