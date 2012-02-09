package controllers.organization

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

import extensions.JJson
import com.mongodb.casbah.Imports._
import models._
//import collection.JavaConversions.mapAsJavaMap
import models.DataSetState._
import java.util.Date
import controllers.{OrganizationController, ShortDataSet}
import play.api.i18n.Messages
import core.indexing.Indexing
import play.api.mvc.{RequestHeader, Result, AnyContent, Action}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSetControl extends OrganizationController {

  def dataSet(orgId: String, spec: String): Action[AnyContent] = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val dataSet = DataSet.findBySpecAndOrgId(spec, orgId)

        val data = if (dataSet == None)
          JJson.generate(ShortDataSet(userName = connectedUser, orgId = orgId, indexingMappingPrefix = "", lockedBy = None))
        else {
          val dS = dataSet.get
          if (DataSet.canEdit(dS, connectedUser)) {
            JJson.generate(
              ShortDataSet(
                id = Some(dS._id),
                spec = dS.spec,
                facts = dS.getFacts,
                userName = dS.getCreator.userName,
                orgId = dS.orgId,
                recordDefinitions = dS.recordDefinitions,
                indexingMappingPrefix = dS.getIndexingMappingPrefix.getOrElse(""),
                visibility = dS.visibility.value,
                lockedBy = dS.lockedBy)
            )
          } else {
            return Action {
              Forbidden("You are not allowed to edit DataSet %s".format(spec))
            }
          }
        }
        Ok(Template('spec -> Option(spec), 'data -> data, 'factDefinitions -> DataSet.factDefinitionList.filterNot(factDef => factDef.automatic), 'recordDefinitions -> RecordDefinition.recordDefinitions.map(rDef => rDef.prefix)))
    }
  }

  def dataSetSubmit(orgId: String, data: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val dataSet = JJson.parse[ShortDataSet](data)
        val spec: String = dataSet.spec

        def processRequest: Result = {
          val factsObject = new BasicDBObject()
          factsObject.putAll(dataSet.facts)

          def buildMappings(recordDefinitions: List[String]): Map[String, Mapping] = {
            val mappings = recordDefinitions.map {
              recordDef => (recordDef, Mapping(format = RecordDefinition.recordDefinitions.filter(rDef => rDef.prefix == recordDef).head))
            }
            mappings.toMap[String, Mapping]
          }

          def updateMappings(recordDefinitions: List[String], mappings: Map[String, Mapping]): Map[String, Mapping] = {
            val existing = mappings.filter(m => recordDefinitions.contains(m._1))
            val keyList = mappings.keys.toList
            val added = recordDefinitions.filter(prefix => !keyList.contains(prefix))
            existing ++ buildMappings(added)
          }

          // TODO handle all "automatic facts"
          factsObject.append("spec", spec)
          factsObject.append("orgId", orgId)

          dataSet.id match {
            // TODO for update, add the operator that appends key-value pairs rather than setting all
            case Some(id) => {
              val existing = DataSet.findOneByID(id).get
              if (!DataSet.canEdit(existing, connectedUser)) Forbidden("You have no rights to edit this DataSet") // todo add return

              val updatedDetails = existing.details.copy(facts = factsObject)
              val updated = existing.copy(
                spec = spec,
                details = updatedDetails,
                mappings = updateMappings(dataSet.recordDefinitions, existing.mappings),
                idxMappings = List(dataSet.indexingMappingPrefix),
                visibility = Visibility.get(dataSet.visibility))
              DataSet.save(updated)
            }
            case None =>
              // TODO for now only owners can do
              if (!isOwner) return Forbidden("You are not allowed to create a DataSet.")

              DataSet.insert(
                DataSet(
                  spec = dataSet.spec,
                  orgId = orgId,
                  user_id = connectedUserId,
                  state = DataSetState.INCOMPLETE,
                  visibility = Visibility.get(dataSet.visibility),
                  lastUploaded = new Date(),
                  details = Details(
                    name = dataSet.facts("name").toString,
                    facts = factsObject,
                    metadataFormat = RecordDefinition("raw", "http://delving.eu/namespaces/raw", "http://delving.eu/namespaces/raw/schema.xsd")
                  ),
                  mappings = buildMappings(dataSet.recordDefinitions),
                  idxMappings = List(dataSet.indexingMappingPrefix)
                )
              )
          }
          Json(dataSet)
        }

        // TODO validation!

        if ("^[A-Za-z0-9-]{3,40}$".r.findFirstIn(spec) == None) {
          BadRequest(JJson.generate(dataSet.copy(errors = Map("ds.spec" -> "Invalid spec format!")))).as(JSON) // todo add return
        }
        else if (dataSet.indexingMappingPrefix.trim.isEmpty || !dataSet.recordDefinitions.contains(dataSet.indexingMappingPrefix)) {
          BadRequest(JJson.generate(dataSet.copy(errors = Map("ds.indexingMappingPrefix" -> "Choose an indexing mapping prefix!")))).as(JSON) // todo add return
        }
        else {
          processRequest
        }
    }
  }


  def index(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId, spec) {
      dataSet => implicit request =>
        dataSet.state match {
          case DISABLED | UPLOADED | ERROR =>
            try {
              DataSet.updateIndexingControlState(dataSet, dataSet.getIndexingMappingPrefix.getOrElse(""), theme.getFacets.map(_.facetName), theme.getSortFields.map(_.sortKey))
              DataSet.updateStateAndIndexingCount(dataSet, DataSetState.QUEUED)
            } catch {
              case rt if rt.getMessage.contains() =>
                // TODO give the user some decent feedback in the interface
                DataSet.updateStateAndIndexingCount(dataSet, DataSetState.ERROR)
                Error(("Unable to index with mapping %s for dataset %s in theme %s. Problably dataset does not have required mapping").format(dataSet.getIndexingMappingPrefix.getOrElse("NONE DEFINED!"), dataSet.name, theme.name))
            }
            Redirect("/organizations/%s/dataset".format(orgId))
          case _ => Error(Messages("organization.datasets.cannotBeIndexed"))
        }
    }
  }

  def reIndex(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId, spec) {
      dataSet => implicit request =>
        dataSet.state match {
          case ENABLED =>
            DataSet.updateIndexingControlState(dataSet, dataSet.getIndexingMappingPrefix.getOrElse(""), theme.getFacets.map(_.facetName), theme.getSortFields.map(_.sortKey))
            DataSet.updateStateAndIndexingCount(dataSet, DataSetState.QUEUED)
            Redirect("/organizations/%s/dataset".format(orgId))
          case _ => Error(Messages("organization.datasets.cannotBeReIndexed"))
        }
    }
  }

  def cancel(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId: String, spec) {
      dataSet => implicit request =>
        dataSet.state match {
          case QUEUED | INDEXING =>
            DataSet.updateStateAndIndexingCount(dataSet, DataSetState.UPLOADED)
            try {
              Indexing.deleteFromSolr(dataSet)
            } catch {
              case _ => DataSet.updateStateAndIndexingCount(dataSet, DataSetState.ERROR)
            }
            Redirect("/organizations/%s/dataset".format(orgId))
          case _ => Error(Messages("organization.datasets.cannotBeCancelled"))
        }
    }
  }

  def state(orgId: String, spec: String): Result = {
    Json(Map("state" -> DataSet.getStateBySpecAndOrgId(spec, orgId).name))
  }

  def indexingStatus(orgId: String, spec: String): Result = {
    val state = DataSet.getIndexingState(orgId, spec) match {
      case (a, b) if a == b => "DONE"
      case (a, b) => ((a.toDouble / b) * 100).round
    }
    Json(Map("status" -> state))
  }

  def disable(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId, spec) {
      dataSet => implicit request =>
        dataSet.state match {
          case QUEUED | INDEXING | ERROR | ENABLED =>
            val updatedDataSet = DataSet.updateStateAndIndexingCount(dataSet, DataSetState.DISABLED)
            Indexing.deleteFromSolr(updatedDataSet)
            Redirect("/organizations/%s/dataset".format(orgId))
          case _ => Error(Messages("organization.datasets.cannotBeDisabled"))
        }
    }
  }

  def enable(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId, spec) {
      dataSet => implicit request =>
        dataSet.state match {
          case DISABLED =>
            DataSet.updateStateAndIndexingCount(dataSet, DataSetState.ENABLED)
            Redirect("/organizations/%s/dataset".format(orgId))
          case _ => Error(Messages("organization.datasets.cannotBeEnabled"))
        }
    }
  }

  def delete(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId, spec) {
      dataSet => implicit request =>
        dataSet.state match {
          case INCOMPLETE | DISABLED | ERROR | UPLOADED =>
            DataSet.delete(dataSet)
            Redirect("/organizations/%s/dataset".format(orgId))
          case _ => Error(Messages("organization.datasets.cannotBeDeleted"))
        }
    }
  }

  def invalidate(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId, spec) {
      dataSet => implicit request =>
        dataSet.state match {
          case DISABLED | ENABLED | UPLOADED | ERROR =>
            DataSet.invalidateHashes(dataSet)
            DataSet.updateStateAndIndexingCount(dataSet, DataSetState.INCOMPLETE)
            Redirect("/organizations/%s/dataset".format(orgId))
          case _ => Error(Messages("organization.datasets.cannotBeInvalidated"))
        }
    }
  }


  def forceUnlock(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId, spec) {
      dataSet => implicit request =>
        DataSet.unlock(DataSet.findBySpecAndOrgId(spec, orgId).get)
        Ok
    }
  }

  def withDataSet(orgId: String, spec: String)(operation: => DataSet => RequestHeader => Result): Action[AnyContent] = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val dataSet = DataSet.findBySpecAndOrgId(spec, orgId).getOrElse(return Action {
          implicit request => NotFound(Messages("organization.datasets.dataSetNotFound", spec))
        })
        // TODO for now only owners can do
        if (!isOwner) return Action {
          implicit request => Forbidden
        }
        operation(dataSet)(request)
    }
  }
}