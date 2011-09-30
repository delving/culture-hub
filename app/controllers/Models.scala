package controllers

import org.bson.types.ObjectId
import org.joda.time.DateTime
import models._
import play.mvc.Util

// ~~ short models, mainly for browsing & displaying things view full rendering

case class ShortUser(id: ObjectId, firstName: String, lastName: String, fullName: String, email: String, userName: String)

case class ShortObject(id: ObjectId, TS_update: DateTime, name: String, shortDescription: String, userName: String)

case class ShortCollection(id: ObjectId, TS_update: DateTime, name: String, shortDescription: String, thumbnail: Option[ObjectId], userName: String)

case class ShortDataSet(id: Option[ObjectId] = None, spec: String = "", facts: Map[String, String] = Map.empty[String, String], recordDefinitions: List[String] = List.empty[String], userName: String, errors: Map[String, String] = Map.empty[String, String])
case class Fact(name: String, prompt: String, value: String)

case class ShortStory(id: ObjectId, TS_update: DateTime, name: String, shortDescription: String, thumbnail: Option[ObjectId], userName: String)

case class ShortLabel(labelType: String, value: String)

case class ShortTheme(id: ObjectId, name: String)

case class Token(id: String, name: String)


case class ListItem(id: ObjectId, title: String, description: String = "", thumbnail: Option[ObjectId] = None, userName: String, fullUserName: String)


// ~~ reference objects

case class CollectionReference(id: ObjectId, name: String)


trait ModelImplicits {

  // TODO temporary! (should be a cache)
  def fullName(userName: String) = models.User.findByUsername(userName, "cultureHub").get.fullname

  // ~~ ShortItems
  implicit def collectionToShort(c: UserCollection) = ShortCollection(c._id, c.TS_update, c.name, c.description.getOrElse(""), c.thumbnail_object_id, c.userName)
  implicit def cListToSCList(cl: List[UserCollection]) = cl map { c => collectionToShort(c) }

  implicit def storyToShort(s: Story) = ShortStory(s._id, s.TS_update, s.name, s.description, s.thumbnail, s.userName)
  implicit def sListToSSList(sl: List[Story]) = sl map { s => storyToShort(s) }

  implicit def objectToShort(o: DObject) = ShortObject(o._id, o.TS_update, o.name, o.description.getOrElse(""), o.userName)
  implicit def oListToSOList(ol: List[DObject]) = ol map { o => objectToShort(o) }

  implicit def userToShort(u: User) = ShortUser(u._id, u.firstName, u.lastName, u.fullname, u.email, u.reference.username)
  implicit def uListToSUList(ul: List[User]) = ul map { u => userToShort(u) }

  implicit def labelListToShortList(ll: List[ObjectId]): List[ShortLabel] = Label.findAllWithIds(ll).toList map { l => ShortLabel(l.labelType, l.value)}

  implicit def themeToShort(t: PortalTheme) = ShortTheme(t._id, t.name)
  implicit def tListToSTList(tl: Seq[PortalTheme]) = tl map { t => themeToShort(t) }

  implicit def dataSetToShort(ds: DataSet) = ShortDataSet(Option(ds._id), ds.spec, ds.getFacts, ds.mappings.keySet.toList, ds.getUser.reference.username)
  implicit def dSListToSdSList(dsl: List[DataSet]) = dsl map { ds => dataSetToShort(ds) }

  // ~~ ListItems

  implicit def objectToListItem(o: DObject) = ListItem(o.id, o.name, o.description.getOrElse(""), Some(o.id), o.userName, fullName(o.userName))
  implicit def collectionToListItem(c: UserCollection) = ListItem(c.id, c.name, c.description.getOrElse(""), c.thumbnail_object_id, c.userName, fullName(c.userName))
  implicit def storyToListItem(s: Story) = ListItem(s.id, s.name, s.description, s.thumbnail, s.userName, fullName(s.userName))
  implicit def userToListItem(u: User) = ListItem(u.id, u.fullname, "", None, u.reference.username, u.fullName)
  implicit def dataSetToListItem(ds: DataSet) = ListItem(ds.id.get, ds.details.name, ds.description.getOrElse(""), None, ds.userName, fullName(ds.userName))

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


}

trait ViewModel {
  val errors: Map[String, String]
  lazy val validationRules: Map[String, String] = util.Validation.getClientSideValidationRules(this.getClass)
}