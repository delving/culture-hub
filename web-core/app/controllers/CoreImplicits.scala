package controllers

import core.Constants._
import org.bson.types.ObjectId
import models.DataSet


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait CoreImplicits {

  // ~~~ ViewModels

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
    userName = ds.getCreator,
    lockedBy = ds.lockedBy)

  implicit def dSListToSdSList(dsl: List[DataSet]) = dsl map { ds => dataSetToShort(ds) }

  // ~~~ ListItems

  implicit def dataSetToListItem(ds: DataSet) = ListItem(ds.spec, DATASET, ds.details.name, "", "", "unknown/unknown", ds.getCreator, false, "/nope")
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
