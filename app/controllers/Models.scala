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

import org.bson.types.ObjectId
import models._

// ~~ short models, mainly for browsing & displaying things view full rendering

case class ShortDataSet(id: Option[ObjectId] = None,
                        spec: String = "",
                        total_records: Int = 0,
                        state: DataSetState = DataSetState.INCOMPLETE,
                        facts: Map[String, String] = Map.empty[String, String],
                        recordDefinitions: List[String] = List.empty[String],
                        orgId: String,
                        userName: String,
                        errors: Map[String, String] = Map.empty[String, String], visibility: Int = 0)

case class Fact(name: String, prompt: String, value: String)

case class ShortLabel(labelType: String, value: String)

case class Token(id: String, name: String, tokenType: String, data: Map[String, String] = Map.empty[String, String])

case class ListItem(id: String,
                    title: String,
                    description: String = "",
                    thumbnail: Option[ObjectId] = None,
                    userName: String,
                    isPrivate: Boolean,
                    url: String)

case class ShortObjectModel(id: String, url: String, thumbnail: String, title: String, hubType: String)

// ~~ reference objects

case class CollectionReference(id: String, name: String)

abstract class ViewModel {
  val errors: Map[String, String]
  lazy val validationRules: Map[String, String] = util.Validation.getClientSideValidationRules(this.getClass)
}