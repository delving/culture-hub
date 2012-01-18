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

import org.bson.types.ObjectId
import java.util.Date
import salatContext._
import com.novus.salat.dao.SalatDAO

/**
 * TODO replace with the Link system
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 11/5/11 11:03 AM  
 */

case class CoReferenceLink(_id: ObjectId = new ObjectId,
                           link_uri: String, // uri
                           link_category: String, // maybe controlled list
                           who: LinkAuthor,
                           from: FromLink,
                           to: ToLink,
                           when: LinkCreation,
                           where: LinkOrigin,
                           why: LinkDescription
                           )
{
  def toXml: String = {
    "<link>implement the rest</link>"
  }

//  def fromXml(xmlString: String): CoReferenceLink = {
//    CoReferenceLink()
//  }

}

object CoReferenceLink extends SalatDAO[CoReferenceLink, ObjectId](collection = CoRefCollecion) { }

case class LinkAuthor(userUri: String,  name: String,  organisation: String, role: String)

case class FromLink (uri: String, linkType: String, title: String, crmClass: String = "", extra: Map[String, Array[String]] = Map.empty)
case class ToLink(uri: String, linkType: String, title: String, crmClass: String = "", extra: Map[String, Array[String]] = Map.empty)

case class LinkCreation(creationDate: Date)
case class LinkOrigin(context: String,  siteUri: String, siteType: String)

case class LinkDescription(linkType: String,  value: String,  note: String,  quality: String)