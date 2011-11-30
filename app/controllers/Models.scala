package controllers

import org.bson.types.ObjectId
import models._

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
                    fullUserName: String,
                    isPrivate: Boolean,
                    childItems: List[ObjectId] = List.empty[ObjectId])


// ~~ reference objects

case class CollectionReference(id: String, name: String)


trait ModelImplicits {

  // TODO temporary! (should be a cache)
  def fullName(userName: String) = models.User.findByUsername(userName).get.fullname

  implicit def oidToString(oid: ObjectId) = oid.toString

  implicit def linkToToken(embeddedLink: EmbeddedLink): Token = Token(embeddedLink.link, embeddedLink.value("label"), embeddedLink.linkType, embeddedLink.value)
  implicit def linkListToTokenList(l: List[EmbeddedLink]) = l.map { linkToToken(_) }

  implicit def dataSetToShort(ds: DataSet) = ShortDataSet(Option(ds._id), ds.spec, ds.details.total_records, ds.state, ds.getFacts, ds.mappings.keySet.toList, ds.orgId, ds.getCreator.userName)
  implicit def dSListToSdSList(dsl: List[DataSet]) = dsl map { ds => dataSetToShort(ds) }

  // ~~ ListItems

  implicit def objectToListItem(o: DObject): ListItem = ListItem(o._id, o.name, o.description, Some(o._id), o.userName, fullName(o.userName), o.visibility == Visibility.PRIVATE)
  implicit def collectionToListItem(c: UserCollection) = ListItem(c._id, c.name, c.description, c.thumbnail_id, c.userName, fullName(c.userName), c.visibility == Visibility.PRIVATE)
  implicit def storyToListItem(s: Story) = ListItem(s._id, s.name, s.description, s.thumbnail_id, s.userName, fullName(s.userName), s.visibility == Visibility.PRIVATE)
  implicit def userToListItem(u: User) = ListItem(u._id, u.fullname, "", None, u.userName, u.fullname, false)
  implicit def dataSetToListItem(ds: DataSet) = ListItem(ds.spec, ds.details.name, ds.description.getOrElse(""), None, ds.getCreator.userName, ds.getCreator.fullname, false)

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