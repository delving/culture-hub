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

import models._
import controllers._
import extensions.JJson
import extensions.Formatters._
import com.mongodb.casbah.Imports._
import play.api.mvc.{AnyContent, Action}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import collection.immutable.List
import core.HubServices
import core.Constants._
import controllers.ListItem
import play.api.data.validation.ValidationError
import models.Details
import models.FormatAccessControl
import models.Mapping
import controllers.ShortDataSet

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DataSetCreationViewModel(id: Option[ObjectId] = None,
                            spec: String = "",
                            facts: HardcodedFacts = HardcodedFacts(),
                            selectedSchemas: Seq[String] = Seq.empty,
                            allAvailableSchemas: Seq[String],
                            schemaProcessingConfigurations: Seq[SchemaProcessingConfiguration],
                            indexingMappingPrefix: Option[String] = None,
                            errors: Map[String, String] = Map.empty) extends ViewModel

case class SchemaProcessingConfiguration(prefix: String,
                                         availableVersions: Seq[String] = Seq("1.0.0"),
                                         version: String = "1.0.0",
                                         accessType: String = "none",
                                         accessKey: String = ""
)

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

object DataSetCreationViewModel {

  private def notRaw: Constraint[Option[String]] = Constraint[Option[String]]("constraint.notRaw") { o =>
    if (o == Some("raw")) Invalid(ValidationError("error.notRaw")) else Valid
  }

  val dataSetForm = Form(
    mapping(
      "id" -> optional(of[ObjectId]),
      "spec" -> nonEmptyText.verifying(Constraints.pattern("^[A-Za-z0-9-]{3,40}$".r, "constraint.validSpec", "Invalid spec format")),
      "facts" -> mapping(
        "name" -> nonEmptyText,
        "language" -> nonEmptyText,
        "country" -> nonEmptyText,
        "provider" -> nonEmptyText,
        "dataProvider" -> nonEmptyText,
        "rights" -> nonEmptyText,
        "type" -> nonEmptyText
      )(HardcodedFacts.apply)(HardcodedFacts.unapply),
      "selectedSchemas" -> seq(text),
      "allAvailableSchemas" -> seq(text),
      "schemaProcessingConfigurations" -> seq(
        mapping(
          "prefix" -> nonEmptyText,
          "availableVersions" -> seq(text),
          "version" -> nonEmptyText,
          "accessType" -> nonEmptyText,
          "accessKey" -> text
        )(SchemaProcessingConfiguration.apply)(SchemaProcessingConfiguration.unapply)
      ),
      "indexingMappingPrefix" -> optional(text).verifying(notRaw),
      "errors" -> of[Map[String, String]]
    )(DataSetCreationViewModel.apply)(DataSetCreationViewModel.unapply)
  )


}

object DataSetControl extends OrganizationController {


  // ~~~ implicit conversions

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

  implicit def dataSetToListItem(ds: DataSet) = ListItem(ds.spec, DATASET, ds.details.name, "", "", "unknown/unknown", ds.getCreator, false, "/nope")
  implicit def dataSetListToListItemList(l: List[DataSet]) = l.map { dataSetToListItem(_) }


  def dataSet(orgId: String, spec: Option[String]): Action[AnyContent] = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        val dataSet = if (spec == None) None else DataSet.dao.findBySpecAndOrgId(spec.get, orgId)
        val allRecordDefinitions: Seq[String] = RecordDefinition.availableSchemas

        if (dataSet != None && !DataSet.dao.canEdit(dataSet.get, connectedUser)) {
          Forbidden("You are not allowed to edit DataSet %s".format(spec.get))
        } else if(dataSet == None && !DataSet.dao.canAdministrate(connectedUser)) {
          Forbidden("You are not allowed to create DataSets")
        } else {
          val data = if (dataSet == None) {
            JJson.generate(DataSetCreationViewModel(
              allAvailableSchemas = allRecordDefinitions,
              schemaProcessingConfigurations = allRecordDefinitions.map { prefix =>
                // TODO once we know them, set available versions, and selected version to the most recent
                SchemaProcessingConfiguration(prefix = prefix)
              },
              indexingMappingPrefix = Some("None")
            ))
        } else {
            val dS = dataSet.get

            def acl(prefix: String) = dS.formatAccessControl.get(prefix).map(f => (f.accessType, f.accessKey)).getOrElse(("none", None))

            JJson.generate(
              DataSetCreationViewModel(
                id = Some(dS._id),
                spec = dS.spec,
                facts = HardcodedFacts.fromMap(dS.getFacts),
                selectedSchemas = dS.recordDefinitions,
                allAvailableSchemas = allRecordDefinitions,
                schemaProcessingConfigurations = allRecordDefinitions.map { prefix =>
                  // TODO versions
                  SchemaProcessingConfiguration(
                    prefix = prefix,
                    accessType = acl(prefix)._1,
                    accessKey = acl(prefix)._2.getOrElse("")
                  )
                },
                indexingMappingPrefix = if(dS.getIndexingMappingPrefix.isEmpty) Some("None") else dS.getIndexingMappingPrefix
              )
            )
          }
          Ok(Template(
            'spec -> spec,
            'data -> data,
            'dataSetForm -> DataSetCreationViewModel.dataSetForm,
            'factDefinitions -> DataSet.factDefinitionList.filterNot(factDef => factDef.automatic || factDef.name == "spec").toList,
            'recordDefinitions -> RecordDefinition.enabledDefinitions(configuration)
          ))
          
        }
    }
  }

  def dataSetSubmit(orgId: String): Action[AnyContent] = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        DataSetCreationViewModel.dataSetForm.bind(request.body.asJson.get).fold(
          formWithErrors => handleValidationError(formWithErrors),
          dataSetForm => {
            val factsObject = new BasicDBObject()
            factsObject.putAll(dataSetForm.facts.asMap)

            // try to enrich with provider and dataProvider uris
            def enrich(input: String, output: String) = HubServices.directoryService(configuration).findOrganizationByName(factsObject.get(input).toString) match {
              case Some(p) => factsObject.put(output, p.uri)
              case None => factsObject.remove(output)
            }

            enrich("provider", "providerUri")
            enrich("dataProvider", "dataProviderUri")

            def buildMappings(schemaProcessingConfigurations: Seq[SchemaProcessingConfiguration]): Map[String, Mapping] = {
              val mappings = schemaProcessingConfigurations.map {
                s => (s.prefix, Mapping(schemaPrefix = s.prefix, schemaVersion = s.version))
              }
              mappings.toMap[String, Mapping]
            }

            def updateMappings(schemaProcessingConfigurations: Seq[SchemaProcessingConfiguration], mappings: Map[String, Mapping]): Map[String, Mapping] = {
              val existing = mappings.filter(m => schemaProcessingConfigurations.exists(s => s.prefix == m._1))
              val keyList = mappings.keys.toList
              val added = schemaProcessingConfigurations.filter(s => !keyList.contains(s.prefix))
              existing ++ buildMappings(added)
            }

            // TODO handle all "automatic facts"
            factsObject.append("spec", dataSetForm.spec)
            factsObject.append("orgId", orgId)

            val formatAccessControl = dataSetForm.schemaProcessingConfigurations.
              filter(a => dataSetForm.selectedSchemas.contains(a.prefix)).
              map(a => (a.prefix -> FormatAccessControl(a.accessType, if (a.accessKey.isEmpty) None else Some(a.accessKey)))).toMap

            val submittedSchemaConfigurations = dataSetForm.selectedSchemas.flatMap(prefix => dataSetForm.schemaProcessingConfigurations.find(p => p.prefix == prefix))

            dataSetForm.id match {
              case Some(id) => {
                val existing = DataSet.dao.findOneById(id).get
                if (!DataSet.dao.canEdit(existing, connectedUser)) {
                  return Action {
                    implicit request => Forbidden("You have no rights to edit this DataSet")
                  }
                }

                val updatedDetails = existing.details.copy(name = dataSetForm.facts.name, facts = factsObject)
                val updated = existing.copy(
                    spec = dataSetForm.spec,
                    details = updatedDetails,
                    mappings = updateMappings(submittedSchemaConfigurations, existing.mappings),
                    formatAccessControl = formatAccessControl,
                    idxMappings = dataSetForm.indexingMappingPrefix.map(List(_)).getOrElse(List.empty)
                  )
                DataSet.dao.save(updated)
                DataSetEvent ! DataSetEvent.Updated(orgId, dataSetForm.spec, connectedUser)
              }
              case None =>
                // TODO for now only admins can do
                if (!DataSet.dao.canAdministrate(connectedUser)) return Action {
                  implicit request => Forbidden("You are not allowed to create a DataSet.")
                }

                DataSet.dao.insert(
                  DataSet(
                    spec = dataSetForm.spec,
                    orgId = orgId,
                    userName = connectedUser,
                    state = DataSetState.INCOMPLETE,
                    details = Details(
                      name = dataSetForm.facts.name,
                      facts = factsObject
                    ),
                    mappings = buildMappings(submittedSchemaConfigurations),
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

  def organizationLookup(orgId: String, term: String) = OrgMemberAction(orgId) {
    Action {
      implicit request =>
        Json(HubServices.directoryService(configuration).findOrganization(term).map(_.name))
    }
  }

}

