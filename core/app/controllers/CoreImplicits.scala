package controllers

import core.Constants._
import org.bson.types.ObjectId
import models.{HubUser, DataSet, EmbeddedLink}


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait CoreImplicits {

  // ~~~ ViewModels

  implicit def linkToToken(embeddedLink: EmbeddedLink): Token = Token(embeddedLink.link.toString, embeddedLink.value("label"), Some(embeddedLink.linkType), Some(embeddedLink.value))
  implicit def linkListToTokenList(l: List[EmbeddedLink]) = l.map { linkToToken(_) }

  implicit def dataSetToShort(ds: DataSet) = ShortDataSet(
    id = Option(ds._id),
    spec = ds.spec,
    total_records = ds.details.total_records,
    state = ds.state,
    errorMessage = ds.errorMessage,
    facts = ds.getFacts,
    recordDefinitions = ds.mappings.keySet.toList,
    indexingMappingPrefix = ds.getIndexingMappingPrefix.getOrElse("NONE"),
    orgId = ds.orgId,
    userName = ds.getCreator.userName,
    lockedBy = ds.lockedBy)

  implicit def dSListToSdSList(dsl: List[DataSet]) = dsl map { ds => dataSetToShort(ds) }

  // ~~~ ListItems

  implicit def userToListItem(u: HubUser) = ListItem(u._id, USER, u.fullname, u.email, None, None, "unknown/unknown", u.userName, false, "/" + u.userName)
  implicit def dataSetToListItem(ds: DataSet) = ListItem(ds.spec, DATASET, ds.details.name, ds.description.getOrElse(""), None, None, "unknown/unknown", ds.getCreator.userName, false, "/nope")

  implicit def userListToListItemList(l: List[HubUser]) = l.map { userToListItem(_) }
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


}
