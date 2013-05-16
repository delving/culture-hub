package plugins

import _root_.services.{ UploadDocumentOrganizationCollectionLookupService, UploadDocumentRecordResolverService }
import play.api.{ Configuration, Application }
import core._
import models.{ OrganizationConfiguration, Role }
import scala.collection.immutable.ListMap
import scala.util.matching.Regex
import play.api.mvc.Handler
import scala.collection.JavaConverters._
import eu.delving.metadata.{ Path, RecDef }
import core.MainMenuEntry
import core.MenuElement
import eu.delving.schema.SchemaType
import java.io.ByteArrayInputStream

class SimpleDocumentUploadPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = SimpleDocumentUploadPlugin.PLUGIN_KEY

  val schemaService: SchemaService = HubModule.inject[SchemaService](name = None)

  private var configurations: Map[OrganizationConfiguration, SimpleDocumentUploadPluginConfiguration] = Map.empty

  def getConfiguration(implicit configuration: OrganizationConfiguration) = configurations.get(configuration).getOrElse {
    throw new RuntimeException("Can't find plugin configuration for configuration named %s, available configurations are: %s".format(
      configuration.orgId, configurations.keys.map(_.orgId) mkString ","
    ))
  }

  /**
   * Called at configuration building time, giving the plugin the chance to build internal configuration
   *
   */
  override def onBuildConfiguration(configurations: Map[OrganizationConfiguration, Option[Configuration]]) {

    this.configurations = configurations.map { config =>

      config._2.map {
        c =>
          {

            val schemaPrefix = c.getString("schemaPrefix").getOrElse(throw missingConfigurationField("schemaPrefix", config._1.orgId))
            val schemaVersion = c.getString("schemaVersion").getOrElse(throw missingConfigurationField("schemaVersion", config._1.orgId))

            val recordDefinitionSchema = schemaService.getSchema(schemaPrefix, schemaVersion, SchemaType.RECORD_DEFINITION).getOrElse {
              throw new RuntimeException("Couldn't find schema %s %s configured for plugin %s".format(
                schemaPrefix, schemaVersion, pluginKey
              ))
            }

            val recDef = RecDef.read(new ByteArrayInputStream(recordDefinitionSchema.getBytes("UTF-8")))

            val fieldTypes = c.underlying.getStringList("fields").asScala.map { f =>
              (f -> Option(recDef.getFieldType(Path.create("/record/" + f))))
            }

            val invalidFields = fieldTypes.filter(f => f._2.isEmpty || f._2.get.isEmpty)
            if (!invalidFields.isEmpty) {
              throw new RuntimeException("Invalid fields configured for plugin %s: %s".format(
                pluginKey, invalidFields.map(_._1).mkString(", ")
              ))
            }

            val validFieldTypes = fieldTypes.map(pair => (pair._1 -> pair._2.get))

            val configuredFields = validFieldTypes.map { f =>
              val optionsPath = """options."%s"""".format(f._1.replaceAll(":", "_"))
              val options = if (c.underlying.hasPath(optionsPath)) {
                Some(c.underlying.getStringList(optionsPath).asScala.toSeq)
              } else {
                None
              }
              val multiplicityPath = """multiplicity."%s"""".format(f._1.replaceAll(":", "_"))
              val multiplicity = c.getInt(multiplicityPath)
              ConfiguredField(key = f._1, fieldType = f._2, hasOptions = !options.isEmpty, options = options.getOrElse(Seq.empty), multiplicity = multiplicity.getOrElse(1))
            }.toSeq

            val uploadConfig = SimpleDocumentUploadPluginConfiguration(
              schemaPrefix = schemaPrefix,
              schemaVersion = schemaVersion,
              titleField = c.getString("titleField").getOrElse(throw missingConfigurationField("titleField", config._1.orgId)),
              fields = configuredFields,
              collectionName = c.getString("collectionName").getOrElse("uploadDocuments")
            )

            (config._1 -> uploadConfig)
          }
      }.getOrElse {
        throw new RuntimeException("%s plugin for OrganizationConfiguration %s is active but not properly configured".format(
          pluginKey, config._1.orgId
        ))
      }

    }

  }

  override def services: Seq[Any] = Seq(
    new UploadDocumentRecordResolverService,
    new UploadDocumentOrganizationCollectionLookupService
  )

  override val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap(
    ("GET", """^/organizations/([A-Za-z0-9-]+)/simpledocument""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organizations.SimpleDocumentUpload.list(pathArgs(0))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/simpledocument/add""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organizations.SimpleDocumentUpload.simpleDocumentUpload(pathArgs(0), None)
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/simpledocument/([A-Za-z0-9-_]+)/update""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organizations.SimpleDocumentUpload.simpleDocumentUpload(pathArgs(0), Some(pathArgs(1)))
    },
    ("POST", """^/organizations/([A-Za-z0-9-]+)/simpledocument/submit""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organizations.SimpleDocumentUpload.submit(pathArgs(0))
    },
    ("DELETE", """^/organizations/([A-Za-z0-9-]+)/simpledocument/([A-Za-z0-9-_]+)/remove""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organizations.SimpleDocumentUpload.delete(pathArgs(0), pathArgs(1))
    },
    ("POST", """^/organizations/([A-Za-z0-9-]+)/simpledocument/upload/([A-Za-z0-9-]+)""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organizations.SimpleDocumentUpload.upload(pathArgs(0), pathArgs(1), queryString("id"))
    }
  )

  /**
   * Override this to add menu entries to the organization menu
   * @param configuration the organization ID
   * @param lang the active language
   * @param roles the roles of the current user
   * @return a sequence of [[core.MainMenuEntry]] for the organization menu
   */
  override def organizationMenuEntries(configuration: OrganizationConfiguration, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "simple-document-upload",
      titleKey = "_sdu.DocumentUpload",
      roles = Seq(Role.OWN, SimpleDocumentUploadPlugin.ROLE_DOCUMENT_EDITOR),
      items = Seq(
        MenuElement(
          url = "/organizations/%s/simpledocument".format(configuration.orgId),
          titleKey = "_sdu.ListOfUploadedDocuments",
          roles = Seq(Role.OWN, SimpleDocumentUploadPlugin.ROLE_DOCUMENT_EDITOR)
        ),
        MenuElement(
          url = "/organizations/%s/simpledocument/add".format(configuration.orgId),
          titleKey = "_sdu.UploadDocument",
          roles = Seq(Role.OWN, SimpleDocumentUploadPlugin.ROLE_DOCUMENT_EDITOR)
        )
      )
    )
  )

  override def roles: Seq[Role] = Seq(SimpleDocumentUploadPlugin.ROLE_DOCUMENT_EDITOR)
}

object SimpleDocumentUploadPlugin {

  val PLUGIN_KEY = "simple-document-upload"

  val ITEM_TYPE = ItemType("uploadDocument")

  lazy val ROLE_DOCUMENT_EDITOR = Role(
    key = "documentUploader",
    description = Map("en" -> "Document upload rights")
  )

  def pluginConfiguration(implicit configuration: OrganizationConfiguration): SimpleDocumentUploadPluginConfiguration = {
    CultureHubPlugin.getEnabledPlugins.find(_.pluginKey == SimpleDocumentUploadPlugin.PLUGIN_KEY).map { p =>
      p.asInstanceOf[SimpleDocumentUploadPlugin].getConfiguration
    }.get // at this point we know that we have the plugin
  }

}

case class SimpleDocumentUploadPluginConfiguration(
  schemaPrefix: String,
  schemaVersion: String,
  fields: Seq[ConfiguredField],
  titleField: String,
  collectionName: String)

case class ConfiguredField(
  key: String,
  fieldType: String,
  hasOptions: Boolean = false,
  options: Seq[String] = Seq.empty,
  multiplicity: Int = 1)