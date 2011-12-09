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
import views.context.thumbnailUrl
import eu.delving.sip.IndexDocument
import com.mongodb.casbah.Imports._

// ~~ short models, mainly for browsing & displaying things view full rendering

case class ShortDataSet(id: Option[ObjectId] = None, spec: String = "", total_records: Int = 0, state: DataSetState = DataSetState.INCOMPLETE, facts: Map[String, String] = Map.empty[String, String], recordDefinitions: List[String] = List.empty[String], orgId: String, userName: String, errors: Map[String, String] = Map.empty[String, String], visibility: Int = 0)
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


trait ModelImplicits {

  implicit def oidToString(oid: ObjectId) = oid.toString

  implicit def linkToToken(embeddedLink: EmbeddedLink): Token = Token(embeddedLink.link, embeddedLink.value("label"), embeddedLink.linkType, embeddedLink.value)
  implicit def linkListToTokenList(l: List[EmbeddedLink]) = l.map { linkToToken(_) }

  implicit def dataSetToShort(ds: DataSet) = ShortDataSet(Option(ds._id), ds.spec, ds.details.total_records, ds.state, ds.getFacts, ds.mappings.keySet.toList, ds.orgId, ds.getCreator.userName)
  implicit def dSListToSdSList(dsl: List[DataSet]) = dsl map { ds => dataSetToShort(ds) }

  implicit def objectToShortObjectModel(o: DObject): ShortObjectModel = ShortObjectModel(o._id, o.url, thumbnailUrl(o.thumbnail_id), o.name, util.Constants.OBJECT)
  implicit def objectListToShortObjectModelList(l: List[DObject]): List[ShortObjectModel] = l.map { objectToShortObjectModel(_) }

  // ~~ ListItems

  implicit def objectToListItem(o: DObject): ListItem = ListItem(o._id, o.name, o.description, Some(o._id), o.userName, o.visibility == Visibility.PRIVATE, o.url)
  implicit def collectionToListItem(c: UserCollection) = ListItem(c._id, c.name, c.description, c.thumbnail_id, c.userName, c.visibility == Visibility.PRIVATE, c.url)
  implicit def storyToListItem(s: Story) = ListItem(s._id, s.name, s.description, s.thumbnail_id, s.userName, s.visibility == Visibility.PRIVATE, s.url)
  implicit def userToListItem(u: User) = ListItem(u._id, u.fullname, u.email, None, u.userName, false, "/" + u.userName)
  implicit def dataSetToListItem(ds: DataSet) = ListItem(ds.spec, ds.details.name, ds.description.getOrElse(""), None, ds.getCreator.userName, false, "/nope")

  implicit def objectListToListItemList(l: List[DObject]) = l.map { objectToListItem(_) }
  implicit def collectionListToListItemList(l: List[UserCollection]) = l.map { collectionToListItem(_) }
  implicit def storyListToListItemList(l: List[Story]) = l.map { storyToListItem(_) }
  implicit def userListToListItemList(l: List[User]) = l.map { userToListItem(_) }
  implicit def dataSetListToListItemList(l: List[DataSet]) = l.map { dataSetToListItem(_) }

  implicit def oidOptionToString(oid: Option[ObjectId]) = oid match {
    case Some(id) => id.toString
    case None => ""
  }

  implicit def stringToOidOption(id: String): Option[ObjectId] = ObjectId.isValid(id) match {
    case true => Some(new ObjectId(id))
    case false => None
  }


  implicit def toDBObject(indexDocument: IndexDocument): DBObject = {
    val m = indexDocument.getMap
    import scala.collection.JavaConverters._
    val values: Map[String, List[String]] = (m.keySet().asScala.map { key =>
    val value: java.util.List[IndexDocument#Value] = m.get(key)
      (key, value.asScala.map(_.toString).toList)
    }).toMap
    values
    val builder = MongoDBObject.newBuilder
    values.keys foreach { k => builder += (k -> values(k))}
    builder.result()
  }

}


trait ViewModel {
  val errors: Map[String, String]
  lazy val validationRules: Map[String, String] = util.Validation.getClientSideValidationRules(this.getClass)
}