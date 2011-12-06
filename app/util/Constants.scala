package util

import eu.delving.metadata.SummaryField

/**
 * Constants, used across several building blocks
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Constants {

  // ~~~ hub types, used in view as itemName and otherwise for links and search recordTypes
  val OBJECT = "object"
  val USERCOLLECTION = "collection"
  val STORY = "story"
  val USER = "user"
  val MDR = "mdr"

  // ~~~ identifier fields
  val ID = "id"
  val HUB_ID = "delving_hubId"
  val PMH_ID = "delving_pmhId"
  val ORG_ID = "delving_orgId"

  val SPEC = "delving_spec"
  val FORMAT = "delving_currentFormat"
  val RECORD_TYPE = "delving_recordType"

  // ~~~ special indexing fields
  val COLLECTIONS = "delving_userCollections"

  // ~~~ "the guys" for access
  val TITLE = SummaryField.TITLE.tag
  val DESCRIPTION = SummaryField.DESCRIPTION.tag
  val OWNER = SummaryField.OWNER.tag
  val CREATOR = SummaryField.CREATOR.tag
  val VISIBILITY = SummaryField.VISIBILITY.tag
  val THUMBNAIL = SummaryField.THUMBNAIL.tag

  // ~~~ link value fields
  val USERCOLLECTION_ID = "userCollectionId" // mongo ID of a collection





}