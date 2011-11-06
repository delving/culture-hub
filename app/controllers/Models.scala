package controllers

import org.bson.types.ObjectId
import models._

// ~~ short models, mainly for browsing & displaying things view full rendering

case class ShortDataSet(id: Option[ObjectId] = None, spec: String = "", total_records: Int = 0, state: DataSetState = DataSetState.INCOMPLETE, facts: Map[String, String] = Map.empty[String, String], recordDefinitions: List[String] = List.empty[String], userName: String, errors: Map[String, String] = Map.empty[String, String])
case class Fact(name: String, prompt: String, value: String)

case class ShortLabel(labelType: String, value: String)

case class Token(id: String, name: String)

case class ListItem(id: String, title: String, description: String = "", thumbnail: Option[ObjectId] = None, userName: String, fullUserName: String)


// ~~ reference objects

case class CollectionReference(id: ObjectId, name: String)


trait ModelImplicits {

  // TODO temporary! (should be a cache)
  def fullName(userName: String) = models.User.findByUsername(userName).get.fullname

  implicit def oidToString(oid: ObjectId) = oid.toString

  // ~~ ShortItems
  implicit def labelListToShortList(ll: List[ObjectId]): List[ShortLabel] = Label.findAllWithIds(ll).toList map { l => ShortLabel(l.labelType, l.value)}

  implicit def dataSetToShort(ds: DataSet) = ShortDataSet(Option(ds._id), ds.spec, ds.details.total_records, ds.state, ds.getFacts, ds.mappings.keySet.toList, ds.getUser.userName)
  implicit def dSListToSdSList(dsl: List[DataSet]) = dsl map { ds => dataSetToShort(ds) }

  // ~~ ListItems

  implicit def objectToListItem(o: DObject): ListItem = ListItem(o._id, o.name, o.description, Some(o._id), o.userName, fullName(o.userName))
  implicit def collectionToListItem(c: UserCollection) = ListItem(c._id, c.name, c.description, c.thumbnail_id, c.userName, fullName(c.userName))
  implicit def storyToListItem(s: Story) = ListItem(s._id, s.name, s.description, s.thumbnail_id, s.userName, fullName(s.userName))
  implicit def userToListItem(u: User) = ListItem(u._id, u.fullname, "", None, u.userName, u.fullname)
  implicit def dataSetToListItem(ds: DataSet) = ListItem(ds.spec, ds.details.name, ds.description.getOrElse(""), None, ds.getUser.userName, ds.getUser.fullname) // TODO store username in DS

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