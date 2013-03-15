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
 * Interface for any kind of object accessible from the view layer
 *
 * TODO review what can be removed from here
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait ViewableItem {

  // ~~~ identifiers
  def getHubId: String

  // ~~~ short view
  def getItemType: String
  def getTitle: String
  def getDescription: String
  def getUri: String // internal landing page for this thing
  def getThumbnailUri: String
  def getThumbnailUri(size: Int): String

  def getOwner: String
  def getVisibility: String
  def getLandingPage: String // external landing page for this thing
  def getMimeType: String
  def hasDigitalObject: Boolean

}