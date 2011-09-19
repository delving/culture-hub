package controllers

import org.bson.types.ObjectId
import org.joda.time.DateTime
import models._

case class ShortUser(id: ObjectId, firstName: String, lastName: String, fullName: String, email: String, userName: String)

case class ShortObject(id: ObjectId, TS_update: DateTime, name: String, shortDescription: String, userName: String)

case class ShortCollection(id: ObjectId, TS_update: DateTime, name: String, shortDescription: String, thumbnail: Option[ObjectId], userName: String)

case class ShortDataSet(id: Option[ObjectId] = None, spec: String = "", facts: Map[String, String] = Map.empty[String, String], recordDefinitions: List[String] = List.empty[String])
case class Fact(name: String, prompt: String, value: String)

case class ShortStory(id: ObjectId, TS_update: DateTime, name: String, shortDescription: String, thumbnail: Option[ObjectId], userName: String)

case class ShortLabel(labelType: String, value: String)

case class ShortTheme(id: ObjectId, name: String)

case class Token(id: String, name: String)


trait ModelImplicits {

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

  implicit def oidOptionToString(oid: Option[ObjectId]) = oid match {
    case Some(id) => id.toString
    case None => ""
  }

  implicit def stringToOidOption(id: String): Option[ObjectId] = ObjectId.isValid(id) match {
    case true => Some(new ObjectId(id))
    case false => None
  }


}