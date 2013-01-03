package controllers

import org.bson.types.ObjectId
import models.DataSetState

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class ShortDataSet(id: Option[ObjectId] = None,
                        spec: String = "",
                        description: String = "",
                        total_records: Long = 0,
                        state: DataSetState = DataSetState.INCOMPLETE,
                        errorMessage: Option[String],
                        facts: Map[String, String] = Map.empty[String, String],
                        recordDefinitions: List[String] = List.empty[String],
                        indexingMappingPrefix: String,
                        orgId: String,
                        userName: String,
                        lockedBy: Option[String],
                        errors: Map[String, String] = Map.empty[String, String],
                        visibility: Int = 0) {

  val error: String = errorMessage.map {
    m => m.replaceAll("\n", "<br/>")
  }.getOrElse("")
}
