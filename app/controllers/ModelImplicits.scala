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
import com.mongodb.casbah.Imports._
import views.context.thumbnailUrl
import eu.delving.sip.IndexDocument
import util.Constants._

/**
 * Implicits for conversion between backend models and view models
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait ModelImplicits extends Internationalization {

  // ~~~ View models

  implicit def linkToToken(embeddedLink: EmbeddedLink): Token = Token(embeddedLink.link.toString, embeddedLink.value("label"), Some(embeddedLink.linkType), Some(embeddedLink.value))
  implicit def linkListToTokenList(l: List[EmbeddedLink]) = l.map { linkToToken(_) }

  implicit def dataSetToShort(ds: DataSet) = ShortDataSet(
    id = Option(ds._id),
    spec = ds.spec,
    total_records = ds.details.total_records,
    state = ds.state,
    facts = ds.getFacts,
    recordDefinitions = ds.mappings.keySet.toList,
    indexingMappingPrefix = ds.getIndexingMappingPrefix.getOrElse("NONE"),
    orgId = ds.orgId,
    userName = ds.getCreator.userName)

  implicit def dSListToSdSList(dsl: List[DataSet]) = dsl map { ds => dataSetToShort(ds) }

  implicit def objectToShortObjectModel(o: DObject): ShortObjectModel = ShortObjectModel(o._id, o.url, thumbnailUrl(o.thumbnail_id), o.name, util.Constants.OBJECT)
  implicit def objectListToShortObjectModelList(l: List[DObject]): List[ShortObjectModel] = l.map { objectToShortObjectModel(_) }

  // ~~ ListItems

  implicit def objectToListItem(o: DObject): ListItem = ListItem(o._id, OBJECT, o.name, o.description, Some(o._id), None, o.userName, o.visibility == Visibility.PRIVATE, o.url)
  implicit def collectionToListItem(c: UserCollection) = ListItem(c._id, USERCOLLECTION, c.name, c.description, c.thumbnail_id, None, c.userName, c.visibility == Visibility.PRIVATE, c.url)
  implicit def storyToListItem(s: Story) = ListItem(s._id, STORY, s.name, s.description, s.thumbnail_id, None,  s.userName, s.visibility == Visibility.PRIVATE, s.url)
  implicit def userToListItem(u: User) = ListItem(u._id, USER, u.fullname, u.email, None, None,  u.userName, false, "/" + u.userName)
  implicit def dataSetToListItem(ds: DataSet) = ListItem(ds.spec, DATASET, ds.details.name, ds.description.getOrElse(""), None, None, ds.getCreator.userName, false, "/nope")

  implicit def objectListToListItemList(l: List[DObject]) = l.map { objectToListItem(_) }
  implicit def collectionListToListItemList(l: List[UserCollection]) = l.map { collectionToListItem(_) }
  implicit def storyListToListItemList(l: List[Story]) = l.map { storyToListItem(_) }
  implicit def userListToListItemList(l: List[User]) = l.map { userToListItem(_) }
  implicit def dataSetListToListItemList(l: List[DataSet]) = l.map { dataSetToListItem(_) }

  // ~~~ ObjectId

  implicit def oidToString(oid: ObjectId): String = oid.toString

  implicit def oidOptionToString(oid: Option[ObjectId]): String = oid match {
    case Some(id) => id.toString
    case None => ""
  }

  implicit def stringToOidOption(id: String): Option[ObjectId] = ObjectId.isValid(id) match {
    case true => Some(new ObjectId(id))
    case false => None
  }

  // ~~~ Misc.

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