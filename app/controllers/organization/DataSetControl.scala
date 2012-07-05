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
import org.bson.types.ObjectId
import models._
import models.DataSetState._
import play.api.i18n.Messages
import play.api.mvc.{RequestHeader, Result, AnyContent, Action}
import play.api.data.Forms._
import collection.JavaConverters._
import extensions.Formatters._
import play.api.data.Form
import play.api.data.validation.Constraints
import controllers.{ViewModel, OrganizationController}
import collection.immutable.List
import play.api.libs.concurrent.Promise
import core.indexing.IndexingService
import core.{DataSetEvent, HubServices}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DataSetViewModel(id: Option[ObjectId] = None,
                            spec: String = "",
                            facts: HardcodedFacts = HardcodedFacts(),
                            //                            facts:                  Map[String, String] = Map.empty,
                            recordDefinitions: Seq[String] = Seq.empty,
                            allRecordDefinitions: List[String],
                            oaiPmhAccess: List[OaiPmhAccessViewModel],
                            indexingMappingPrefix: Option[String] = None,
                            errors: Map[String, String] = Map.empty) extends ViewModel

case class OaiPmhAccessViewModel(format: String, accessType: String = "none", accessKey: Option[String] = None)

case class HardcodedFacts(name: String = "",
                          language: String = "",
                          country: String = "",
                          provider: String = "",
                          dataProvider: String = "",
                          rights: String = "",
                          `type`: String = "") {
  def asMap = Map(
    "name" -> name,
    "language" -> language,
    "country" -> country,
    "provider" -> provider,
    "dataProvider" -> dataProvider,
    "rights" -> rights,
    "type" -> `type`
  )
}

object HardcodedFacts {

  def fromMap(map: Map[String, String]) = HardcodedFacts(
    name = map.get("name").getOrElse(""),
    language = map.get("language").getOrElse(""),
    country = map.get("country").getOrElse(""),
    provider = map.get("provider").getOrElse(""),
    dataProvider = map.get("dataProvider").getOrElse(""),
    rights = map.get("rights").getOrElse(""),
    `type` = map.get("type").getOrElse("")
  )
}

object DataSetViewModel {

  val dataSetForm = Form(
    mapping(
      "id" -> optional(of[ObjectId]),
      "spec" -> nonEmptyText.verifying(Constraints.pattern("^[A-Za-z0-9-]{3,40}$".r, "constraint.validSpec", "Invalid spec format")),
      //      "facts" -> of[Map[String, String]],
      "facts" -> mapping(
        "name" -> nonEmptyText,
        "language" -> nonEmptyText,
        "country" -> nonEmptyText,
        "provider" -> nonEmptyText,
        "dataProvider" -> nonEmptyText,
        "rights" -> nonEmptyText,
        "type" -> nonEmptyText
      )(HardcodedFacts.apply)(HardcodedFacts.unapply),
      "recordDefinitions" -> seq(text),
      "allRecordDefinitions" -> list(text),
      "oaiPmhAccess" -> list(
        mapping(
          "format" -> nonEmptyText,
          "accessType" -> nonEmptyText,
          "accessKey" -> optional(text)
        )(OaiPmhAccessViewModel.apply)(OaiPmhAccessViewModel.unapply)
      ),
      "indexingMappingPrefix" -> optional(text),
      "errors" -> of[Map[String, String]]
    )(DataSetViewModel.apply)(DataSetViewModel.unapply)
  )


}

object DataSetControl extends OrganizationController {

  def dataSet(orgId: String, spec: Option[String]): Action[AnyContent] = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val dataSet = if (spec == None) None else DataSet.findBySpecAndOrgId(spec.get, orgId)
        val allRecordDefinitions: List[String] = RecordDefinition.recordDefinitions.map(r => r.prefix).toList

        val data = if (dataSet == None) {
          JJson.generate(DataSetViewModel(
            allRecordDefinitions = allRecordDefinitions,
            oaiPmhAccess = RecordDefinition.recordDefinitions.map(rDef => OaiPmhAccessViewModel(rDef.prefix))
          ))
        } else {
          val dS = dataSet.get
          if (DataSet.canEdit(dS, connectedUser)) {
            JJson.generate(
              DataSetViewModel(
                id = Some(dS._id),
                spec = dS.spec,
                facts = HardcodedFacts.fromMap(dS.getFacts),
                recordDefinitions = dS.recordDefinitions,
                allRecordDefinitions = allRecordDefinitions,
                oaiPmhAccess = dS.formatAccessControl.map(e => OaiPmhAccessViewModel(e._1, e._2.accessType, e._2.accessKey)).toList,
                indexingMappingPrefix = dS.getIndexingMappingPrefix
              )
            )
          } else {
            return Action {
              Forbidden("You are not allowed to edit DataSet %s".format(spec))
            }
          }
        }

        Ok(Template(
          'spec -> spec,
          'data -> data,
          'dataSetForm -> DataSetViewModel.dataSetForm,
          'factDefinitions -> DataSet.factDefinitionList.filterNot(factDef => factDef.automatic || factDef.name == "spec").toList,
          'recordDefinitions -> RecordDefinition.recordDefinitions.map(rDef => rDef.prefix)
        ))
    }
  }

  def dataSetSubmit(orgId: String): Action[AnyContent] = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        DataSetViewModel.dataSetForm.bind(request.body.asJson.get).fold(
          formWithErrors => handleValidationError(formWithErrors),
          dataSetForm => {
            val factsObject = new BasicDBObject()
            factsObject.putAll(dataSetForm.facts.asMap)

            // try to enrich with provider and dataProvider uris
            def enrich(input: String, output: String) = HubServices.directoryService.findOrganizationByName(factsObject.get(input).toString) match {
              case Some(p) => factsObject.put(output, p.uri)
              case None => factsObject.remove(output)
            }

            enrich("provider", "providerUri")
            enrich("dataProvider", "dataProviderUri")

            def buildMappings(recordDefinitions: Seq[String]): Map[String, Mapping] = {
              val mappings = recordDefinitions.map {
                recordDef => (recordDef, Mapping(format = RecordDefinition.recordDefinitions.filter(rDef => rDef.prefix == recordDef).head))
              }
              mappings.toMap[String, Mapping]
            }

            def updateMappings(recordDefinitions: Seq[String], mappings: Map[String, Mapping]): Map[String, Mapping] = {
              val existing = mappings.filter(m => recordDefinitions.contains(m._1))
              val keyList = mappings.keys.toList
              val added = recordDefinitions.filter(prefix => !keyList.contains(prefix))
              existing ++ buildMappings(added)
            }

            // TODO handle all "automatic facts"
            factsObject.append("spec", dataSetForm.spec)
            factsObject.append("orgId", orgId)

            val formatAccessControl = dataSetForm.oaiPmhAccess.
              filter(a => dataSetForm.recordDefinitions.contains(a.format)).
              map(a => (a.format -> FormatAccessControl(a.accessType, a.accessKey))).toMap


            dataSetForm.id match {
              // TODO for update, add the operator that appends key-value pairs rather than setting all
              case Some(id) => {
                val existing = DataSet.findOneById(id).get
                if (!DataSet.canEdit(existing, connectedUser)) {
                  return Action {
                    implicit request => Forbidden("You have no rights to edit this DataSet")
                  }
                }

                val updatedDetails = existing.details.copy(facts = factsObject)
                val updated = existing.copy(
                    spec = dataSetForm.spec,
                    details = updatedDetails,
                    mappings = updateMappings(dataSetForm.recordDefinitions, existing.mappings),
                    formatAccessControl = formatAccessControl,
                    idxMappings = dataSetForm.indexingMappingPrefix.map(List(_)).getOrElse(List.empty)
                  )
                DataSet.save(updated)
                DataSetEvent ! DataSetEvent.Updated(orgId, dataSetForm.spec, connectedUser)
              }
              case None =>
                // TODO for now only owners can do
                if (!isAdmin(orgId)) return Action {
                  implicit request => Forbidden("You are not allowed to create a DataSet.")
                }

                DataSet.insert(
                  DataSet(
                    spec = dataSetForm.spec,
                    orgId = orgId,
                    userName = connectedUser,
                    state = DataSetState.INCOMPLETE,
                    details = Details(
                      name = dataSetForm.facts.name,
                      facts = factsObject
                    ),
                    mappings = buildMappings(dataSetForm.recordDefinitions),
                    formatAccessControl = formatAccessControl,
                    idxMappings = dataSetForm.indexingMappingPrefix.map(List(_)).getOrElse(List.empty)
                  )
                )

                DataSetEvent ! DataSetEvent.Created(orgId, dataSetForm.spec, connectedUser)

            }
            Json(dataSetForm)
          }
        )
    }
  }


  def index(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId, spec) {
      dataSet => implicit request =>
        dataSet.state match {
          case ENABLED | UPLOADED | DISABLED | ERROR =>
            try {
              DataSet.updateIndexingControlState(dataSet, dataSet.getIndexingMappingPrefix.getOrElse(""), theme.getFacets.map(_.facetName), theme.getSortFields.map(_.sortKey))
              DataSet.updateStateAndProcessingCount(dataSet, connectedUser, DataSetState.QUEUED)

              Redirect("/organizations/%s/dataset".format(orgId))
            } catch {
              case t =>
                DataSet.updateStateAndProcessingCount(dataSet, connectedUser, DataSetState.ERROR, Some(t.getMessage))
                Error(("Unable to index with mapping %s for dataset %s in theme %s. Problably dataset does not have required mapping").format(dataSet.getIndexingMappingPrefix.getOrElse("NONE DEFINED!"), dataSet.getName, theme.name))
            }
          case _ => Error(Messages("organization.datasets.cannotBeProcessed"))
        }
    }
  }

  def reIndex(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId, spec) {
      dataSet => implicit request =>
        dataSet.state match {
          case ENABLED | UPLOADED | DISABLED | ERROR =>
            DataSet.updateIndexingControlState(dataSet, dataSet.getIndexingMappingPrefix.getOrElse(""), theme.getFacets.map(_.facetName), theme.getSortFields.map(_.sortKey))
            DataSet.updateStateAndProcessingCount(dataSet, connectedUser, DataSetState.QUEUED)
            Redirect("/organizations/%s/dataset".format(orgId))
          case _ => Error(Messages("organization.datasets.cannotBeReProcessed"))
        }
    }
  }

  def cancel(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId: String, spec) {
      dataSet => implicit request =>
        dataSet.state match {
          case QUEUED | PROCESSING =>
            DataSet.updateStateAndProcessingCount(dataSet, connectedUser, DataSetState.CANCELLED)
            try {
              IndexingService.deleteBySpec(dataSet.orgId, dataSet.spec)
            } catch {
              case t => DataSet.updateStateAndProcessingCount(dataSet, connectedUser, DataSetState.ERROR, Some(t.getMessage))
            }
            Redirect("/organizations/%s/dataset".format(orgId))
          case _ => Error(Messages("organization.datasets.cannotBeCancelled"))
        }
    }
  }

  def state(orgId: String, spec: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        Async {
          Promise.pure(DataSet.getState(orgId, spec).name).map {
            response => Json(Map("state" -> response))
          }
        }
    }
  }

  def indexingStatus(orgId: String, spec: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val state = DataSet.getProcessingState(orgId, spec) match {
          case (a, b) if a == b && a == 100 => "DONE"
          case (a, b) if a == b && a == 0 => "STARTING"
          case (a, b) => ((a.toDouble / b) * 100).round
        }
        Json(Map("status" -> state))
    }
  }

  def disable(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId, spec) {
      dataSet => implicit request =>
        dataSet.state match {
          case QUEUED | PROCESSING | ERROR | ENABLED =>
            val updatedDataSet = DataSet.updateStateAndProcessingCount(dataSet, connectedUser, DataSetState.DISABLED)
            IndexingService.deleteBySpec(updatedDataSet.orgId, updatedDataSet.spec)
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
            DataSet.updateStateAndProcessingCount(dataSet, connectedUser, DataSetState.ENABLED)
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
            DataSetEvent ! DataSetEvent.Removed(orgId, spec, connectedUser)
            Redirect("/organizations/%s/dataset".format(orgId))
          case _ => Error(Messages("organization.datasets.cannotBeDeleted"))
        }
    }
  }

  def invalidate(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId, spec) {
      dataSet => implicit request =>
        dataSet.state match {
          case DISABLED | ENABLED | UPLOADED | ERROR | PARSING =>
            DataSet.invalidateHashes(dataSet)
            DataSet.updateStateAndProcessingCount(dataSet, connectedUser, DataSetState.INCOMPLETE)

            Redirect("/organizations/%s/dataset".format(orgId))
          case _ => Error(Messages("organization.datasets.cannotBeInvalidated"))
        }
    }
  }


  def forceUnlock(orgId: String, spec: String): Action[AnyContent] = {
    withDataSet(orgId, spec) {
      dataSet => implicit request =>
        DataSet.unlock(DataSet.findBySpecAndOrgId(spec, orgId).get)
        DataSetEvent ! DataSetEvent.Unlocked(orgId, spec, connectedUser)
        Ok
    }
  }

  def organizationLookup(orgId: String, term: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        Json(HubServices.directoryService.findOrganization(term).map(_.name))
    }
  }

  def withDataSet(orgId: String, spec: String)(operation: => DataSet => RequestHeader => Result): Action[AnyContent] = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val dataSet = DataSet.findBySpecAndOrgId(spec, orgId).getOrElse(return Action {
          implicit request => NotFound(Messages("organization.datasets.dataSetNotFound", spec))
        })
        // TODO for now only owners can do
        if (!isAdmin(orgId)) return Action {
          implicit request => Forbidden
        }
        operation(dataSet)(request)
    }
  }
}

