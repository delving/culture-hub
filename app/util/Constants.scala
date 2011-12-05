package util

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
  val DATASET = "dataset"
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
  val TITLE = "title"
  val DESCRIPTION = "description"
  val OWNER = "owner"
  val CREATOR = "creator"
  val VISIBILITY = "delving_visibility"
  val THUMBNAIL = "thumbnail"

  // ~~~ link value fields
  val USERCOLLECTION_ID = "userCollectionId" // mongo ID of a collection





}