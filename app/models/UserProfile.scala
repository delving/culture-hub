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

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class UserProfile(isPublic: Boolean = false,
                   description: Option[String] = None,
                   funFact: Option[String] = None,
                   // place: EmbeddedLink, // TODO
                   websites: List[String] = List.empty[String],
                   twitter: Option[String] = None,
                   facebook: Option[String] = None,
                   linkedIn: Option[String] = None)
