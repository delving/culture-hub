package models {

import _root_.core.node.Node
import core.access.ResourceType
import core.{SystemField, CultureHubPlugin}
import org.apache.solr.client.solrj.SolrQuery
import core.search.{SolrSortElement, SolrFacetElement}
import play.api.{Configuration, Play, Logger}
import Play.current
import collection.JavaConverters._
import collection.mutable.ArrayBuffer

/**
 * Holds configuration that is used when a specific domain is accessed. It overrides a default configuration.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 */

case class DomainConfiguration(

  // ~~~ core
  name:                        String,
  orgId:                       String,
  domains:                     List[String] = List.empty,

  // ~~~ mail
  emailTarget:                 EmailTarget = EmailTarget(),

  // ~~~ data
  mongoDatabase:               String,
  baseXConfiguration:          BaseXConfiguration,
  solrBaseUrl:                 String,

  // ~~~ services
  commonsService:              CommonsServiceConfiguration,
  objectService:               ObjectServiceConfiguration,
  oaiPmhService:               OaiPmhServiceConfiguration,
  searchService:               SearchServiceConfiguration,
  directoryService:            DirectoryServiceConfiguration,

  plugins:                     Seq[String],

  // ~~~ schema
  schemas:                     Seq[String],
  crossWalks:                  Seq[String],

  // ~~~ user interface
  ui:                          UserInterfaceConfiguration,

  // ~~~ access control
  registeredUsersAddedToOrg:   Boolean = false,
  roles:                       Seq[Role],

  // ~~~ search
  apiWsKey:                    Boolean = false

) {

  val self = this

  /**
   * The node for this organization hub
   */
  val node = new Node {
    val nodeId: String = commonsService.nodeName
    val name: String = commonsService.nodeName
    val orgId: String = self.orgId
    val isLocal: Boolean = true
  }

  def getFacets: List[SolrFacetElement] = {
    searchService.facets.split(",").filter(k => k.split(":").size > 0 && k.split(":").size < 4).map {
      entry => {
        val k = entry.split(":")
        k.length match {
          case 1 => SolrFacetElement(k.head, k.head)
          case 2 => SolrFacetElement(k(0), k(1))
          case 3 =>
            try {
              SolrFacetElement(k(0), k(1), k(2).toInt)
            } catch {
              case  _ : java.lang.NumberFormatException =>
                Logger("CultureHub").warn("Wrong value %s for facet display column number for theme %s".format(k(2), name))
                SolrFacetElement(k(0), k(1))
            }
        }
      }
    }.toList
  }

  def getSortFields: List[SolrSortElement] = {
    searchService.sortFields.split(",").filter(sf => sf.split(":").size > 0 && sf.split(":").size < 3).map {
      entry => {
        val k = entry.split(":")
        k.length match {
          case 1 => SolrSortElement(k.head)
          case 2 =>
            SolrSortElement(
              k(1),
              if (k(2).equalsIgnoreCase("desc")) SolrQuery.ORDER.desc else SolrQuery.ORDER.asc
            )
        }
      }
    }.toList
  }

}

case class UserInterfaceConfiguration(
  themeDir:                    String,
  defaultLanguage:             String = "en",
  siteName:                    Option[String],
  siteSlogan:                  Option[String],
  footer:                      Option[String],
  addThisTrackingCode:         Option[String],
  googleAnalyticsTrackingCode: Option[String],
  showLogin:                   Boolean,
  showRegistration:            Boolean,
  showAllObjects:              Boolean
)

case class CommonsServiceConfiguration(
  commonsHost:                 String,
  nodeName:                    String,
  apiToken:                    String
)

case class ObjectServiceConfiguration(
  fileStoreDatabaseName:       String,
  imageCacheDatabaseName:      String,
  imageCacheEnabled:           Boolean,
  tilesOutputBaseDir:          String,
  tilesWorkingBaseDir:         String
)

case class OaiPmhServiceConfiguration(
  repositoryName:              String,
  adminEmail:                  String,
  earliestDateStamp:           String,
  repositoryIdentifier:        String,
  sampleIdentifier:            String,
  responseListSize:            Int,
  allowRawHarvesting:          Boolean
)

case class DirectoryServiceConfiguration(
  providerDirectoryUrl:        String
)

case class SearchServiceConfiguration(
  hiddenQueryFilter:            String,
  facets:                       String, // dc_creator:crea:Creator,dc_type
  sortFields:                   String, // dc_creator,dc_provider:desc
  moreLikeThis:                 MoreLikeThis,
  searchIn:                     Map[String, String],
  apiWsKey:                     Boolean = false,
  pageSize:                     Int,
  showResultsWithoutThumbnails: Boolean = false
)

/** See http://wiki.apache.org/solr/MoreLikeThis **/
case class MoreLikeThis(
  fieldList: Seq[String] = Seq(SystemField.DESCRIPTION.tag, "dc_creator_text"),
  minTermFrequency: Int = 1,
  minDocumentFrequency: Int = 2,
  minWordLength: Int = 0,
  maxWordLength: Int = 0,
  maxQueryTerms: Int = 25,
  maxNumToken: Int = 5000,
  boost: Boolean = false,
  count: Int = 5,
  queryFields: Seq[String] = Seq()
)

case class BaseXConfiguration(
  host: String,
  port: Int,
  eport: Int,
  user: String,
  password: String
)

case class EmailTarget(adminTo: String = "test-user@delving.eu",
                       exceptionTo: String = "test-user@delving.eu",
                       feedbackTo: String = "test-user@delving.eu",
                       registerTo: String = "test-user@delving.eu",
                       systemFrom: String = "noreply@delving.eu",
                       feedbackFrom: String = "noreply@delving.eu")

object DomainConfiguration {

  val log = Logger("CultureHub")

  // ~~~ keys
  val ORG_ID = "orgId"

  val SOLR_BASE_URL = "solr.baseUrl"
  val MONGO_DATABASE = "mongoDatabase"

  val COMMONS_HOST = "services.commons.host"
  val COMMONS_NODE_NAME = "services.commons.nodeName"
  val COMMONS_API_TOKEN = "services.commons.apiToken"

  val PROVIDER_DIRECTORY_URL = "services.directory.providerDirectoryUrl"

  val REGISTRATION_USERS_ADDED_TO_ORG = "registration.registeredUsersAddedToOrganization"

  val FILESTORE_DATABASE = "services.dos.fileStoreDatabase"
  val IMAGE_CACHE_DATABASE = "services.dos.imageCacheDatabase"
  val IMAGE_CACHE_ENABLED = "services.dos.imageCacheEnabled"
  val TILES_WORKING_DIR = "services.dos.tilesWorkingBaseDir"
  val TILES_OUTPUT_DIR = "services.dos.tilesOutputBaseDir"

  val PLUGINS = "plugins"

  val SCHEMAS = "schemas"
  val CROSSWALKS = "crossWalks"

  val SEARCH_HQF = "services.search.hiddenQueryFilter"
  val SEARCH_FACETS = "services.search.facets"
  val SEARCH_SORTFIELDS = "services.search.sortFields"
  val SEARCH_APIWSKEY = "services.search.apiWsKey"
  val SEARCH_MORELIKETHIS = "services.search.moreLikeThis"
  val SEARCH_SEARCHIN = "services.search.searchIn"
  val SEARCH_PAGE_SIZE = "services.search.pageSize"
  val SHOW_ITEMS_WITHOUT_THUMBNAIL = "services.search.showItemsWithoutThumbnail"

  val OAI_REPO_NAME = "services.pmh.repositoryName"
  val OAI_ADMIN_EMAIL = "services.pmh.adminEmail"
  val OAI_EARLIEST_TIMESTAMP = "services.pmh.earliestDateStamp"
  val OAI_REPO_IDENTIFIER = "services.pmh.repositoryIdentifier"
  val OAI_SAMPLE_IDENTIFIER = "services.pmh.sampleIdentifier"
  val OAI_RESPONSE_LIST_SIZE = "services.pmh.responseListSize"
  val OAI_ALLOW_RAW_HARVESTING = "services.pmh.allowRawHarvesting"

  val BASEX_HOST = "basex.host"
  val BASEX_PORT = "basex.port"
  val BASEX_EPORT = "basex.eport"
  val BASEX_USER = "basex.user"
  val BASEX_PASSWORD = "basex.password"

  val EMAIL_ADMINTO = "emailTarget.adminTo"
  val EMAIL_EXCEPTIONTO = "emailTarget.exceptionTo"
  val EMAIL_FEEDBACKTO = "emailTarget.feedbackTo"
  val EMAIL_REGISTERTO = "emailTarget.registerTo"
  val EMAIL_SYSTEMFROM = "emailTarget.systemFrom"
  val EMAIL_FEEDBACKFROM = "emailTarget.feedbackFrom"


  val MANDATORY_OVERRIDABLE_KEYS = Seq(
    SOLR_BASE_URL,
    COMMONS_HOST, COMMONS_NODE_NAME,
    IMAGE_CACHE_DATABASE, FILESTORE_DATABASE, TILES_WORKING_DIR, TILES_OUTPUT_DIR,
    OAI_REPO_NAME, OAI_ADMIN_EMAIL, OAI_EARLIEST_TIMESTAMP, OAI_REPO_IDENTIFIER, OAI_SAMPLE_IDENTIFIER, OAI_RESPONSE_LIST_SIZE, OAI_ALLOW_RAW_HARVESTING,
    SEARCH_FACETS, SEARCH_SORTFIELDS, SEARCH_APIWSKEY,
    BASEX_HOST, BASEX_PORT, BASEX_EPORT, BASEX_USER, BASEX_PASSWORD,
    PROVIDER_DIRECTORY_URL,
    EMAIL_ADMINTO, EMAIL_EXCEPTIONTO, EMAIL_FEEDBACKTO, EMAIL_REGISTERTO, EMAIL_SYSTEMFROM, EMAIL_FEEDBACKFROM
  )

  val MANDATORY_DOMAIN_KEYS = Seq(ORG_ID, MONGO_DATABASE, COMMONS_API_TOKEN, IMAGE_CACHE_ENABLED, SCHEMAS, CROSSWALKS, PLUGINS)


  /**
   * Computes all domain configurations based on the default Play configuration mechanism.
   */
  def startup(plugins: Seq[CultureHubPlugin]) = {

      var missingKeys = new collection.mutable.HashMap[String, Seq[String]]

      val config = Play.configuration.getConfig("configurations").get
      val allDomainConfigurations: Seq[String] = config.keys.filterNot(_.indexOf(".") < 0).map(_.split("\\.").head).toList.distinct
      val configurations: Seq[DomainConfiguration] = allDomainConfigurations.flatMap {
        configurationKey => {
          val configuration = config.getConfig(configurationKey).get

          // check if all mandatory values are provided
          val missing = MANDATORY_OVERRIDABLE_KEYS.filter(
            key =>
              !configuration.keys.contains(key) &&
              !Play.configuration.keys.contains(key)
          ) ++ MANDATORY_DOMAIN_KEYS.filter(!configuration.keys.contains(_))

          // more checks
          val domains = configuration.underlying.getStringList("domains").asScala
          if (domains.isEmpty) {
            missingKeys += (
              configurationKey -> (
                missingKeys.get(configurationKey).map(list => list ++ Seq("domains")).getOrElse(Seq("domains"))
              )
            )
          }

          if (!missing.isEmpty) {
            missingKeys += (configurationKey -> missing)
            None
          } else {
            Some(
              DomainConfiguration(
                name = configurationKey,
                orgId = configuration.getString(ORG_ID).get,
                domains = configuration.underlying.getStringList("domains").asScala.toList,
                mongoDatabase = configuration.getString(MONGO_DATABASE).get,
                baseXConfiguration = BaseXConfiguration(
                  host = getString(configuration, BASEX_HOST),
                  port = getInt(configuration, BASEX_PORT),
                  eport = getInt(configuration, BASEX_EPORT),
                  user = getString(configuration, BASEX_USER),
                  password = getString(configuration, BASEX_PASSWORD)
                ),
                solrBaseUrl = getString(configuration, SOLR_BASE_URL),
                commonsService = CommonsServiceConfiguration(
                  commonsHost = getString(configuration, COMMONS_HOST),
                  nodeName = configuration.getString(COMMONS_NODE_NAME).get,
                  apiToken = configuration.getString(COMMONS_API_TOKEN).get
                ),
                oaiPmhService = OaiPmhServiceConfiguration(
                  repositoryName = getString(configuration, OAI_REPO_NAME),
                  adminEmail = getString(configuration, OAI_ADMIN_EMAIL),
                  earliestDateStamp = getString(configuration, OAI_EARLIEST_TIMESTAMP),
                  repositoryIdentifier = getString(configuration, OAI_REPO_IDENTIFIER),
                  sampleIdentifier = getString(configuration, OAI_SAMPLE_IDENTIFIER),
                  responseListSize = getInt(configuration, OAI_RESPONSE_LIST_SIZE),
                  allowRawHarvesting = getBoolean(configuration, OAI_ALLOW_RAW_HARVESTING)
                ),
                objectService = ObjectServiceConfiguration(
                  fileStoreDatabaseName = getString(configuration, FILESTORE_DATABASE),
                  imageCacheDatabaseName = getString(configuration, IMAGE_CACHE_DATABASE),
                  imageCacheEnabled = configuration.getBoolean(IMAGE_CACHE_ENABLED).getOrElse(false),
                  tilesWorkingBaseDir = getString(configuration, TILES_WORKING_DIR),
                  tilesOutputBaseDir = getString(configuration, TILES_OUTPUT_DIR)
                ),
                directoryService = DirectoryServiceConfiguration(
                  providerDirectoryUrl = configuration.getString(PROVIDER_DIRECTORY_URL).getOrElse("")
                ),
                searchService = SearchServiceConfiguration(
                  hiddenQueryFilter = getOptionalString(configuration, SEARCH_HQF).getOrElse(""),
                  facets = getString(configuration, SEARCH_FACETS),
                  sortFields = getString(configuration, SEARCH_SORTFIELDS),
                  apiWsKey = getBoolean(configuration, SEARCH_APIWSKEY),
                  moreLikeThis = {
                    val mlt = configuration.getConfig(SEARCH_MORELIKETHIS)
                    val default = MoreLikeThis()
                    if(mlt.isEmpty) {
                      default
                    } else {
                      MoreLikeThis(
                        fieldList = mlt.get.underlying.getStringList("fieldList").asScala,
                        minTermFrequency = mlt.get.getInt("minimumTermFrequency").getOrElse(default.minTermFrequency),
                        minDocumentFrequency = mlt.get.getInt("minimumDocumentFrequency").getOrElse(default.minDocumentFrequency),
                        minWordLength = mlt.get.getInt("minWordLength").getOrElse(default.minWordLength),
                        maxWordLength = mlt.get.getInt("maxWordLength").getOrElse(default.maxWordLength),
                        maxQueryTerms = mlt.get.getInt("maxQueryTerms").getOrElse(default.maxQueryTerms),
                        maxNumToken = mlt.get.getInt("maxNumToken").getOrElse(default.maxNumToken),
                        boost = mlt.get.getBoolean("boost").getOrElse(default.boost),
                        count = mlt.get.getInt("count").getOrElse(default.count),
                        queryFields = mlt.get.underlying.getStringList("queryFields").asScala
                      )
                    }
                  },
                  searchIn = {
                    configuration.getConfig(SEARCH_SEARCHIN).map { searchIn =>
                      searchIn.keys.map { field =>
                        (field -> searchIn.getString(field).getOrElse(""))
                      }.toMap
                    }.getOrElse {
                      Map(
                        "dc_title" -> "metadata.dc.title",
                        "dc_creator" -> "metadata.dc.creator",
                        "dc_subject" -> "metadata.dc.subject"
                      )
                    }
                  },
                  pageSize = getOptionalInt(configuration, SEARCH_PAGE_SIZE).getOrElse(20),
                  showResultsWithoutThumbnails = getOptionalBoolean(configuration, SHOW_ITEMS_WITHOUT_THUMBNAIL).getOrElse(false)
                ),
                plugins = configuration.underlying.getStringList(PLUGINS).asScala.toSeq,
                schemas = configuration.underlying.getStringList(SCHEMAS).asScala.toList,
                crossWalks = configuration.underlying.getStringList(CROSSWALKS).asScala.toList,
                ui = UserInterfaceConfiguration(
                  themeDir = configuration.getString("ui.themeDir").getOrElse("default"),
                  defaultLanguage = configuration.getString("ui.defaultLanguage").getOrElse("en"),
                  siteName = configuration.getString("ui.siteName"),
                  siteSlogan = configuration.getString("ui.siteSlogan").orElse(Some("Delving CultureHub")),
                  footer = configuration.getString("ui.footer").orElse(Some("")),
                  addThisTrackingCode = configuration.getString("ui.addThisTrackingCode").orElse(None),
                  googleAnalyticsTrackingCode = configuration.getString("ui.googleAnalyticsTrackingCode").orElse(None),
                  showLogin = configuration.getBoolean("ui.showLogin").getOrElse(false),
                  showRegistration =  configuration.getBoolean("ui.showRegistration").getOrElse(false),
                  showAllObjects =  configuration.getBoolean("ui.showAllObjects").getOrElse(false)
                ),
                emailTarget = {
                  EmailTarget(
                    adminTo = getString(configuration, EMAIL_ADMINTO),
                    exceptionTo = getString(configuration, EMAIL_EXCEPTIONTO),
                    feedbackTo = getString(configuration, EMAIL_FEEDBACKTO),
                    registerTo = getString(configuration, EMAIL_REGISTERTO),
                    systemFrom = getString(configuration, EMAIL_SYSTEMFROM),
                    feedbackFrom = getString(configuration, EMAIL_FEEDBACKFROM)
                  )
                },
                roles = configuration.getConfig("roles").map {
                  roles => roles.keys.map {
                    key => {
                      val roleKey = key.split("\\.").head
                      // TODO parse all kind of languages
                      val roleDescriptions: Map[String, String] = roles.keys.filter(_.startsWith(roleKey + ".description.")).map {
                        descriptionKey => (descriptionKey.split("\\.").reverse.head -> roles.getString(descriptionKey).getOrElse(""))
                      }.toMap
                      Role(roleKey, roleDescriptions)
                    }
                  }.toSeq
                }.getOrElse(Seq.empty),
                registeredUsersAddedToOrg = getOptionalBoolean(configuration, REGISTRATION_USERS_ADDED_TO_ORG).getOrElse(false)
              )
            )
          }
        }
      }.toList

    // if there's anything wrong, we promptly refuse to start
    if (!missingKeys.isEmpty) {
      log.error(
        """Invalid configuration(s), hence we won't start:
          |%s
        """.stripMargin.format(
          missingKeys.map { config =>
            """
              |== %s:
              |Missing keys: %s
            """.stripMargin.format(
              config._1,
              config._2.mkString(", ")
            )
          }.mkString("\n")
        )
      )
      throw new RuntimeException("Invalid configuration. ¿Satan, is this you?")
    }

    val duplicateOrgIds = configurations.groupBy(_.orgId).filter(_._2.size > 1)
    if (!duplicateOrgIds.isEmpty) {
      log.error(
        "Found domain configurations that use the same orgId: " +
              duplicateOrgIds.map(t => t._1 + ": " + t._2.map(_.name).mkString(", ")).mkString(", "))
      throw new RuntimeException("Invalid configuration. Come back tomorrow.")
    }

    if(configurations.isEmpty) {
      log.error("No domain configuration found. You need to have at least one configured in order to start.")
      throw new RuntimeException("Invalid configuration. No can do.")
    }


    // plugin time! now that we read all the configuration, enrich it with things provided by the plugins

    // start by doing sanity check on plugins

    val duplicatePluginKeys = plugins.groupBy(_.pluginKey).filter(_._2.size > 1)
    if (!duplicatePluginKeys.isEmpty) {
      log.error(
        "Found two or more plugins with the same pluginKey: " +
              duplicatePluginKeys.map(t => t._1 + ": " + t._2.map(_.getClass).mkString(", ")).mkString(", ")
      )
      throw new RuntimeException("Plugin inconsistency. No can do.")
    }

    if (!Play.isTest) {
      val invalidPluginKeys: Seq[(DomainConfiguration, String, Option[CultureHubPlugin])] = configurations.flatMap { configuration =>
        configuration.plugins.map(key => Tuple3(configuration, key, plugins.find(_.pluginKey == key))).filter(_._3.isEmpty)
      }
      if(!invalidPluginKeys.isEmpty) {

        val error = "Found two or more configurations that reference non-existing plugins:\n" +
                      invalidPluginKeys.map(r => "Configuration " + r._1.name + ": " + r._2 + " does not exist or is not available").mkString("\n")

        log.error(error)
        throw new RuntimeException("Role definition inconsistency. No can do.\n\n" + error)
      }
    }

    // access control subsystem: check roles and resource handlers defined by plugins

    val duplicateRoleKeys = plugins.flatMap(plugin => plugin.roles.map(r => (r -> plugin.pluginKey))).groupBy(_._1.key).filter(_._2.size > 1)
    if(!duplicateRoleKeys.isEmpty) {
      val error = "Found two or more roles with the same key: " +
                    duplicateRoleKeys.map(r => r._1 + ": " + r._2.map(pair => "Plugin " + pair._2).mkString(", ")).mkString(", ")

      log.error(error)
      throw new RuntimeException("Role definition inconsistency. No can do.\n\n" + error)
    }

    // make sure that if a Role defines a ResourceType, its declaring plugin also provides a ResourceLookup
    val triplets = new ArrayBuffer[(CultureHubPlugin, Role, ResourceType)]()
    plugins.foreach { plugin =>
      plugin.roles.foreach { role =>
        if(role.resourceType.isDefined) {
          val isResourceLookupProvided = plugin.resourceLookups.exists(lookup => lookup.resourceType == role.resourceType.get)
          if (!isResourceLookupProvided) {
            triplets += Tuple3(plugin, role, role.resourceType.get)
          }
        }
      }
    }
    if(!triplets.isEmpty) {
      log.error(
        """Found plugin-defined role(s) that do not provide a ResourceLookup for their ResourceType:
          |
          |Plugin\t\tRole\t\tResourceType
          |
          |%s
        """.stripMargin.format(
          triplets.map { t =>
            """%s\t\t%s\t\t%s""".format(
              t._1.pluginKey, t._2.key, t._3.resourceType
            )
          }.mkString("\n")
        )
      )
      throw new RuntimeException("Resource definition inconsistency. No can do.")
    }

    // now we're good to go...

    val enhancedConfigurations = configurations.map { configuration =>
      val pluginRoles: Seq[Role] = configuration.plugins.flatMap(key => plugins.find(_.pluginKey == key)).flatMap(_.roles)

      val duplicateRolesDefinition = configuration.roles.filter(configRole => pluginRoles.exists(_.key == configRole.key))
      if(!duplicateRolesDefinition.isEmpty) {
        log.error(
          "Configuration %s defines role(s) that are already provided via plugins: %s".format(
            configuration.name, duplicateRolesDefinition.map(_.key).mkString(", ")
          )
        )
        throw new RuntimeException("Configuration Roles + Plugin Roles clash.")
      }

      configuration.copy(roles = configuration.roles ++ pluginRoles)
    }

    val configs = if (Play.isTest) {
      enhancedConfigurations.map { c =>
        c.copy(
          mongoDatabase = c.mongoDatabase + "-TEST",
          solrBaseUrl = "http://localhost:8983/solr/test",
          objectService = c.objectService.copy(
            fileStoreDatabaseName = c.objectService.fileStoreDatabaseName + "-TEST",
            imageCacheDatabaseName = c.objectService.imageCacheDatabaseName + "-TEST"
          )
        )
      }
    } else {
      enhancedConfigurations
    }


    // when everything else is ready, do the plugin configuration, per plugin

    val pluginConfigurations: Map[(String, DomainConfiguration), Option[Configuration]] = configs.flatMap { configuration =>
      configuration.plugins.map { pluginKey =>
        ((pluginKey -> configuration) -> config.getConfig("%s.plugin.%s".format(configuration.name, pluginKey)))
      }
    }.toMap

    val groupedPluginConfigurations: Map[String, Map[DomainConfiguration, Option[Configuration]]] = pluginConfigurations.groupBy(_._1._1).map { g =>
      (g._1 -> {
        g._2.map(group => (group._1._2 -> group._2))
      })
    }.toMap

    groupedPluginConfigurations.foreach { pluginConfig =>
      CultureHubPlugin.hubPlugins.find(_.pluginKey == pluginConfig._1).map(_.onBuildConfiguration(pluginConfig._2))
    }

    configs

  }


  private def getString(configuration: Configuration, key: String): String =
    configuration.getString(key).getOrElse(Play.configuration.getString(key).get)

  private def getOptionalString(configuration: Configuration, key: String): Option[String] =
    configuration.getString(key).orElse(Play.configuration.getString(key))

  private def getInt(configuration: Configuration, key: String): Int =
    configuration.getInt(key).getOrElse(Play.configuration.getInt(key).get)

  private def getOptionalInt(configuration: Configuration, key: String): Option[Int] =
    configuration.getInt(key).orElse(Play.configuration.getInt(key))

  private def getBoolean(configuration: Configuration, key: String): Boolean =
    configuration.getBoolean(key).getOrElse(Play.configuration.getBoolean(key).get)

  private def getOptionalBoolean(configuration: Configuration, key: String): Option[Boolean] =
    configuration.getBoolean(key).orElse(Play.configuration.getBoolean(key))

}

}