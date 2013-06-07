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
import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import scala.collection.immutable.List
import core._
import scala.collection.JavaConverters._
import xml.Node
import eu.delving.schema.SchemaType
import play.api.data.Forms.mapping
import play.api.data.validation.ValidationError
import models.Details
import models.FormatAccessControl
import models.Mapping
import models.FactDefinition
import controllers.ShortDataSet
import play.api.i18n.Messages
import core.messages._
import com.escalatesoft.subcut.inject.BindingModule
import eu.delving.schema.xml.Schema

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DataSetCreationViewModel(
  id: Option[ObjectId] = None,
  spec: String = "",
  description: String = "",
  facts: HardcodedFacts = HardcodedFacts(),
  selectedSchemas: Seq[String] = Seq.empty,
  allAvailableSchemas: Seq[String],
  schemaProcessingConfigurations: Seq[SchemaProcessingConfiguration],
  indexingMappingPrefix: Option[String] = None)

case class SchemaProcessingConfiguration(
  prefix: String,
  availableVersions: Seq[String],
  version: String,
  accessType: String = "none",
  accessKey: String = "")

case class HardcodedFacts(
    name: String = "",
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

  def specTaken(implicit configuration: OrganizationConfiguration) = Constraint[DataSetCreationViewModel]("dataset.ThisDatasetIdentifierIsAlreadyInUse") {
    case r =>
      val maybeOne = DataSet.dao.findBySpecAndOrgId(r.spec, configuration.orgId)
      if (maybeOne.isDefined && r.id.isDefined) {
        Valid
      } else if (maybeOne == None) {
        Valid
      } else {
        Invalid(ValidationError(Messages("dataset.ThisDatasetIdentifierIsAlreadyInUse")))
      }
  }

  def creationQuotaExceeded(implicit configuration: OrganizationConfiguration) = Constraint[DataSetCreationViewModel]("dataset.TheQuotaOfAllowedDatasetsExceeded") {
    case r =>
      if (CultureHubPlugin.isQuotaExceeded(DataSet.RESOURCE_TYPE)) {
        Invalid(ValidationError(Messages("dataset.TheQuotaOfAllowedDatasetsExceeded")))
      } else {
        Valid
      }
  }

  def dataSetForm(implicit configuration: OrganizationConfiguration) = Form(
    mapping(
      "id" -> optional(of[ObjectId]),
      "spec" -> nonEmptyText.verifying(Constraints.pattern("^[A-Za-z0-9-]{3,40}$".r, "constraint.validSpec", "Invalid spec format")),
      "description" -> text,
      "facts" -> mapping(
        "name" -> nonEmptyText.verifying(Constraints.pattern("^[A-Za-z0-9-_ ]*$".r, "constraint.validName", "Invalid name")),
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
      "indexingMappingPrefix" -> optional(text).verifying(notRaw)
    )(DataSetCreationViewModel.apply)(DataSetCreationViewModel.unapply).verifying(specTaken, creationQuotaExceeded)
  )

}

class DataSetControl(implicit val bindingModule: BindingModule) extends OrganizationController {

  val schemaService = inject[SchemaService]
  val directoryServiceLocator = inject[DomainServiceLocator[DirectoryService]]

  lazy val factDefinitionList = parseFactDefinitionList
  lazy val initialFacts = factDefinitionList.map(factDef => (factDef.name, "")).toMap[String, String]

  // ~~~ implicit conversions

  implicit def dataSetToShort(ds: DataSet) = ShortDataSet(
    id = Option(ds._id),
    spec = ds.spec,
    description = ds.description.getOrElse(""),
    total_records = ds.details.total_records,
    state = ds.state,
    errorMessage = ds.errorMessage,
    facts = (ds.getStoredFacts ++ initialFacts),
    recordDefinitions = ds.mappings.keySet.toList,
    indexingMappingPrefix = ds.getIndexingMappingPrefix.getOrElse("NONE"),
    orgId = ds.orgId,
    userName = ds.getCreator,
    lockedBy = ds.lockedBy)

  implicit def dSListToSdSList(dsl: List[DataSet]) = dsl map { ds => dataSetToShort(ds) }

  private def schemaInformation(implicit configuration: OrganizationConfiguration): (Seq[Schema], Seq[String], Map[String, Seq[String]]) = {
    val schemas = schemaService.getSchemas

    val allSchemaPrefixes: Seq[String] = schemas.map(_.prefix) ++
      (if (configuration.oaiPmhService.allowRawHarvesting) Seq("raw") else Seq.empty)

    val versions: Map[String, Seq[String]] = schemas.map { schema =>
      (schema.prefix -> schema.versions.asScala.map(_.number))
    }.toMap ++
      (if (configuration.oaiPmhService.allowRawHarvesting) Map("raw" -> Seq("1.0.0")) else Map.empty)

    (schemas, allSchemaPrefixes, versions)

  }

  def add = OrganizationMember {
    Action {
      implicit request =>
        if (!DataSet.dao.canAdministrate(connectedUser)) {
          Forbidden("You are not allowed to create DataSets")
        } else {
          val (_, allSchemaPrefixes, versions) = schemaInformation
          val data = JJson.generate(DataSetCreationViewModel(
            allAvailableSchemas = allSchemaPrefixes,
            schemaProcessingConfigurations = allSchemaPrefixes.map { prefix =>
              SchemaProcessingConfiguration(
                prefix = prefix,
                availableVersions = versions(prefix),
                version = versions(prefix).sorted.head
              )
            },
            indexingMappingPrefix = Some("None")
          ))

          Ok(Template("organization/DataSets/dataSet.html",
            'spec -> None,
            'data -> data,
            'creationQuotaExceeded -> CultureHubPlugin.isQuotaExceeded(DataSet.RESOURCE_TYPE),
            'dataSetForm -> DataSetCreationViewModel.dataSetForm,
            'factDefinitions -> factDefinitionList.filterNot(factDef => factDef.automatic || factDef.name == "spec").toList
          ))
        }
    }
  }

  def update(spec: String): Action[AnyContent] = OrganizationMember {
    Action {
      implicit request =>

        val maybeDataSet: Option[DataSet] = DataSet.dao.findBySpecAndOrgId(spec, configuration.orgId)

        maybeDataSet.map { set =>
          {
            if (!DataSet.dao.canEdit(set, connectedUser)) {
              super.Forbidden(s"You are not allowed to edit DataSet $spec")
            } else {
              val data = {

                def acl(prefix: String) = set.formatAccessControl.get(prefix).map(f => (f.accessType, f.accessKey)).getOrElse(("none", None))
                val (_, allSchemaPrefixes, versions) = schemaInformation

                JJson.generate(
                  DataSetCreationViewModel(
                    id = Some(set._id),
                    spec = set.spec,
                    description = set.description.getOrElse(""),
                    facts = HardcodedFacts.fromMap(set.getStoredFacts),
                    selectedSchemas = set.recordDefinitions,
                    allAvailableSchemas = allSchemaPrefixes,
                    schemaProcessingConfigurations = allSchemaPrefixes.map { prefix =>
                      SchemaProcessingConfiguration(
                        prefix = prefix,
                        availableVersions = versions(prefix),
                        version = set.mappings.get(prefix).map(_.schemaVersion).getOrElse(versions(prefix).headOption.getOrElse("")),
                        accessType = acl(prefix)._1,
                        accessKey = acl(prefix)._2.getOrElse("")
                      )
                    },
                    indexingMappingPrefix = if (set.getIndexingMappingPrefix.isEmpty) Some("None") else set.getIndexingMappingPrefix
                  )
                )
              }

              Ok(
                Template("organization/DataSets/dataSet.html",
                  'spec -> Some(spec),
                  'data -> data,
                  'creationQuotaExceeded -> false,
                  'dataSetForm -> DataSetCreationViewModel.dataSetForm,
                  'factDefinitions -> factDefinitionList.filterNot(factDef => factDef.automatic || factDef.name == "spec").toList
                )
              )
            }
          }
        } getOrElse {
          Results.NotFound(s"Couldn't find DataSet $spec")
        }
    }
  }

  def dataSetSubmit = OrganizationMember {
    Action {
      implicit request =>
        DataSetCreationViewModel.dataSetForm.bind(request.body.asJson.get).fold(
          formWithErrors => handleValidationError(formWithErrors),
          dataSetForm => {
            val factsObject = new BasicDBObject()
            factsObject.putAll(dataSetForm.facts.asMap)

            // try to enrich with provider and dataProvider uris
            def enrich(input: String, output: String) = directoryServiceLocator.byDomain.findOrganizationByName(factsObject.get(input).toString) match {
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

            def removedSchemas(schemaProcessingConfigurations: Seq[SchemaProcessingConfiguration], existingMappings: Map[String, Mapping]): Seq[String] = {
              existingMappings.keys.filter(e => !schemaProcessingConfigurations.exists(_.prefix == e)).toSeq
            }

            def updateMappings(schemaProcessingConfigurations: Seq[SchemaProcessingConfiguration], mappings: Map[String, Mapping]): Map[String, Mapping] = {
              val existing = mappings.filter(m => schemaProcessingConfigurations.exists(s => s.prefix == m._1))
              val keyList = mappings.keys.toList
              val added = schemaProcessingConfigurations.filter(s => !keyList.contains(s.prefix))
              val removed = removedSchemas(schemaProcessingConfigurations, mappings)
              val updated = existing.map(e => (e._1 -> e._2.copy(schemaVersion = schemaProcessingConfigurations.find(_.prefix == e._1).get.version)))
              (updated ++ buildMappings(added)).filterNot(entry => removed.contains(entry._1))
            }

            def strToOpt(str: String) = if (str.trim.isEmpty) None else Some(str.trim)

            // TODO handle all "automatic facts"
            factsObject.append("spec", dataSetForm.spec)
            factsObject.append("orgId", configuration.orgId)

            val formatAccessControl = dataSetForm.schemaProcessingConfigurations.
              filter(a => dataSetForm.selectedSchemas.contains(a.prefix)).
              map(a => (a.prefix -> FormatAccessControl(a.accessType, if (a.accessKey.isEmpty) None else Some(a.accessKey)))).toMap

            val submittedSchemaConfigurations = dataSetForm.selectedSchemas.flatMap(prefix => dataSetForm.schemaProcessingConfigurations.find(p => p.prefix == prefix))

            factsObject.append("schemaVersions", submittedSchemaConfigurations.map(c => "%s_%s".format(c.prefix, c.version)).mkString(", "))

            dataSetForm.id match {
              case Some(id) => {
                val existing = DataSet.dao.findOneById(id).get
                if (!DataSet.dao.canEdit(existing, connectedUser)) {
                  Forbidden("You have no rights to edit this DataSet")
                } else {

                  if (existing.spec != dataSetForm.spec) {
                    log.info(s"Renaming DataSet spec ${existing.spec} to ${dataSetForm.spec}")
                    HubServices.basexStorages.getResource(configuration).renameCollection(existing, dataSetForm.spec)
                    indexingServiceLocator.byDomain.deleteBySpec(configuration.orgId, existing.spec)
                    CultureHubPlugin.broadcastMessage(CollectionRenamed(existing.spec, dataSetForm.spec, configuration))
                  }

                  val removed: Seq[String] = removedSchemas(submittedSchemaConfigurations, existing.mappings)
                  val updatedDetails = existing.details.copy(
                    name = dataSetForm.facts.name,
                    facts = factsObject,
                    invalidRecordCount = existing.details.invalidRecordCount.filterNot(i => removed.contains(i._1))
                  )
                  val updatedInvalidRecords = existing.invalidRecords.filterNot(e => removed.contains(e._1))

                  val updated = existing.copy(
                    spec = dataSetForm.spec,
                    description = strToOpt(dataSetForm.description),
                    details = updatedDetails,
                    mappings = updateMappings(submittedSchemaConfigurations, existing.mappings),
                    formatAccessControl = formatAccessControl,
                    invalidRecords = updatedInvalidRecords,
                    idxMappings = dataSetForm.indexingMappingPrefix.map(List(_)).getOrElse(List.empty)
                  )
                  DataSet.dao.save(updated)
                  DataSetEvent ! DataSetEvent.Updated(configuration.orgId, dataSetForm.spec, connectedUser)
                }
              }

              case None =>
                // TODO for now only admins can do
                if (!DataSet.dao.canAdministrate(connectedUser)) {
                  Forbidden("You are not allowed to create a DataSet.")
                } else {
                  DataSet.dao.insert(
                    DataSet(
                      spec = dataSetForm.spec,
                      orgId = configuration.orgId,
                      userName = connectedUser,
                      description = strToOpt(dataSetForm.description),
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

                  DataSetEvent ! DataSetEvent.Created(configuration.orgId, dataSetForm.spec, connectedUser)
                  CultureHubPlugin.broadcastMessage(CollectionCreated(dataSetForm.spec, configuration))

                }

            }
            Json(dataSetForm)
          }
        )
    }
  }

  def organizationLookup(orgId: String, term: String) = OrganizationMember {
    Action {
      implicit request =>
        Json(directoryServiceLocator.byDomain.findOrganization(term).map(_.name))
    }
  }

  // ~~~

  private def parseFactDefinitionList: Seq[FactDefinition] = {
    schemaService.getSchema("facts", "1.0.2", SchemaType.FACT_DEFINITIONS).map { source =>
      val xml = scala.xml.XML.loadString(source)
      for (e <- (xml \ "fact-definition")) yield parseFactDefinition(e)
    }.getOrElse(Seq.empty)
  }

  private def parseFactDefinition(node: Node) = {
    FactDefinition(
      node \ "@name" text,
      node \ "prompt" text,
      node \ "toolTip" text,
      (node \ "automatic" text).equalsIgnoreCase("true"),
      for (option <- (node \ "options" \ "string")) yield (option text)
    )
  }

}