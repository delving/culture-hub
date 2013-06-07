package models {

  import _root_.core.node.Node
  import core.{ SystemField, CultureHubPlugin }
  import core.search.{ SortElement, FacetElement }
  import play.api.{ Configuration, Play, Logger }
  import Play.current
  import collection.JavaConverters._
  import collection.mutable.ArrayBuffer

  /**
   * Holds configuration that is used when a specific domain is accessed. It overrides a default configuration.
   *
   * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
   * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
   */

  case class OrganizationConfiguration(

      // ~~~ core
      orgId: String,
      domains: List[String] = List.empty,
      instances: List[String] = List.empty,
      isReadOnly: Boolean = false,

      // ~~~ mail
      emailTarget: EmailTarget = EmailTarget(),

      // ~~~ data
      mongoDatabase: String,
      baseXConfiguration: BaseXConfiguration,
      solrBaseUrl: String,
      solrIndexerUrl: Option[String],

      // ~~~ services
      commonsService: CommonsServiceConfiguration,
      objectService: ObjectServiceConfiguration,
      oaiPmhService: OaiPmhServiceConfiguration,
      searchService: SearchServiceConfiguration,
      directoryService: DirectoryServiceConfiguration,
      processingService: ProcessingServiceConfiguration,

      // ~~~ quotas
      // the quota key in the configuration should be the ResourceType key being set
      quotas: Map[String, Int] = Map.empty,

      plugins: Seq[String],

      // ~~~ schema
      schemas: Seq[String],
      crossWalks: Seq[String],

      // ~~~ user interface
      ui: UserInterfaceConfiguration,

      // ~~~ access control
      registeredUsersAddedToOrg: Boolean = false,
      roles: Seq[Role]) {

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

    def getFacets: List[FacetElement] = {
      searchService.facets.split(",").filter(k => k.split(":").size > 0 && k.split(":").size < 4).map {
        entry =>
          {
            val k = entry.split(":")
            k.length match {
              case 1 => FacetElement(k.head, k.head)
              case 2 => FacetElement(k(0), k(1))
              case 3 =>
                try {
                  FacetElement(k(0), k(1), k(2).toInt)
                } catch {
                  case _: java.lang.NumberFormatException =>
                    Logger("CultureHub").warn("Wrong value %s for facet display column number for theme %s".format(k(2), orgId))
                    FacetElement(k(0), k(1))
                }
            }
          }
      }.toList
    }

    def getSortFields: List[SortElement] = {
      searchService.sortFields.split(",").filter(sf => sf.split(":").size > 0 && sf.split(":").size < 3).map {
        entry =>
          {
            val k = entry.split(":")
            k.length match {
              case 1 => SortElement(k.head)
              case 2 =>
                SortElement(
                  k(1),
                  (k(2).equalsIgnoreCase("asc"))
                )
            }
          }
      }.toList
    }

    // we compare these using only the orgId, as the configuration may change dynamically at runtime

    override def hashCode(): Int = orgId.hashCode()

    override def equals(other: Any): Boolean = other.isInstanceOf[OrganizationConfiguration] &&
      other.asInstanceOf[OrganizationConfiguration].orgId.equals(orgId)
  }

  case class UserInterfaceConfiguration(
    themeDir: String,
    defaultLanguage: String = "en",
    siteName: Option[String],
    siteSlogan: Option[String],
    footer: Option[String],
    addThisTrackingCode: Option[String],
    googleAnalyticsTrackingCode: Option[String],
    showLogin: Boolean,
    showRegistration: Boolean,
    showAllObjects: Boolean)

  case class CommonsServiceConfiguration(
    commonsHost: String,
    nodeName: String,
    apiToken: String)

  case class ObjectServiceConfiguration(
    fileStoreDatabaseName: String,
    imageCacheDatabaseName: String,
    imageCacheEnabled: Boolean,
    tilesOutputBaseDir: String,
    tilesWorkingBaseDir: String,
    graphicsMagickCommand: String)

  case class OaiPmhServiceConfiguration(
    repositoryName: String,
    adminEmail: String,
    earliestDateStamp: String,
    repositoryIdentifier: String,
    sampleIdentifier: String,
    responseListSize: Int,
    allowRawHarvesting: Boolean)

  case class DirectoryServiceConfiguration(
    providerDirectoryUrl: String)

  case class SearchServiceConfiguration(
    hiddenQueryFilter: String,
    facets: String, // dc_creator:crea:Creator,dc_type
    sortFields: String, // dc_creator,dc_provider:desc
    moreLikeThis: MoreLikeThis,
    searchIn: Map[String, String],
    apiWsKeyEnabled: Boolean = false,
    apiWsKeys: Seq[String] = Seq.empty,
    pageSize: Int,
    rowLimit: Int = 500,
    showResultsWithoutThumbnails: Boolean = false)

  case class ProcessingServiceConfiguration(
    mappingCpuProportion: Double = 0.5)

  /** See http://wiki.apache.org/solr/MoreLikeThis **/
  case class MoreLikeThis(
    fieldList: Seq[String] = Seq(SystemField.DESCRIPTION.tag, "dc_creator_text", "dc_subject_text"),
    minTermFrequency: Int = 1,
    minDocumentFrequency: Int = 2,
    minWordLength: Int = 0,
    maxWordLength: Int = 0,
    maxQueryTerms: Int = 25,
    maxNumToken: Int = 5000,
    boost: Boolean = false,
    count: Int = 5,
    queryFields: Seq[String] = Seq())

  case class BaseXConfiguration(
    host: String,
    port: Int,
    eport: Int,
    user: String,
    password: String)

  case class EmailTarget(adminTo: String = "test-user@delving.eu",
    exceptionTo: String = "test-user@delving.eu",
    feedbackTo: String = "test-user@delving.eu",
    registerTo: String = "test-user@delving.eu",
    systemFrom: String = "noreply@delving.eu",
    feedbackFrom: String = "noreply@delving.eu")

  object OrganizationConfiguration {

    val log = Logger("CultureHub")

    // ~~~ keys
    val ORG_ID = "orgId"

    val INSTANCES = "instances"
    val READ_ONLY = "readOnly"

    val SOLR_BASE_URL = "solr.baseUrl"
    val SOLR_INDEXER_URL = "solr.indexerUrl"
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
    val GM_COMMAND = "services.dos.graphicsmagic.cmd"

    val PLUGINS = "plugins"

    val SCHEMAS = "schemas"
    val CROSSWALKS = "crossWalks"

    val SEARCH_HQF = "services.search.hiddenQueryFilter"
    val SEARCH_FACETS = "services.search.facets"
    val SEARCH_SORTFIELDS = "services.search.sortFields"
    val SEARCH_APIWSKEYENABLED = "services.search.apiWsKeyEnabled"
    val SEARCH_APIWSKEYS = "services.search.apiWsKeys"
    val SEARCH_MORELIKETHIS = "services.search.moreLikeThis"
    val SEARCH_SEARCHIN = "services.search.searchIn"
    val SEARCH_PAGE_SIZE = "services.search.pageSize"
    val SEARCH_ROW_LIMIT = "services.search.rowLimit"
    val SHOW_ITEMS_WITHOUT_THUMBNAIL = "services.search.showItemsWithoutThumbnail"

    val PROCESSING_MAPPING_CPU_PROPORTION = "services.processing.mappingCpuProportion"

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

    val CULTUREHUB_INSTANCE_IDENTIFIER = "cultureHub.instanceIdentifier"

    val MANDATORY_OVERRIDABLE_KEYS = Seq(
      SOLR_BASE_URL,
      CULTUREHUB_INSTANCE_IDENTIFIER,
      COMMONS_HOST, COMMONS_NODE_NAME,
      IMAGE_CACHE_DATABASE, FILESTORE_DATABASE, TILES_WORKING_DIR, TILES_OUTPUT_DIR, GM_COMMAND,
      OAI_REPO_NAME, OAI_ADMIN_EMAIL, OAI_EARLIEST_TIMESTAMP, OAI_REPO_IDENTIFIER, OAI_SAMPLE_IDENTIFIER, OAI_RESPONSE_LIST_SIZE, OAI_ALLOW_RAW_HARVESTING,
      SEARCH_FACETS, SEARCH_SORTFIELDS,
      BASEX_HOST, BASEX_PORT, BASEX_EPORT, BASEX_USER, BASEX_PASSWORD,
      PROVIDER_DIRECTORY_URL,
      EMAIL_ADMINTO, EMAIL_EXCEPTIONTO, EMAIL_FEEDBACKTO, EMAIL_REGISTERTO, EMAIL_SYSTEMFROM, EMAIL_FEEDBACKFROM
    )

    val MANDATORY_DOMAIN_KEYS = Seq(ORG_ID, INSTANCES, MONGO_DATABASE, COMMONS_API_TOKEN, IMAGE_CACHE_ENABLED, SCHEMAS, CROSSWALKS, PLUGINS)

    /**
     * Computes all domain configurations based on a Typesafe configuration and the set of available plugins
     */
    def buildConfigurations(mainConfig: Configuration, plugins: Seq[CultureHubPlugin]): (Seq[OrganizationConfiguration], Seq[(String, String)]) = {

      val allErrors = new ArrayBuffer[(String, String)]()

      val config: Configuration = mainConfig.getConfig("configurations").get
      val allOrganizationConfigurations: Seq[String] = config.keys.filterNot(_.indexOf(".") < 0).map(_.split("\\.").head).toList.distinct

      val parsedConfigurations: Seq[Either[(String, Seq[String]), OrganizationConfiguration]] = allOrganizationConfigurations.map { configurationKey =>
        val configuration = config.getConfig(configurationKey).get

        // check if all mandatory values are provided
        val missingMandatory: Seq[String] = MANDATORY_OVERRIDABLE_KEYS.filter { key =>
          !configuration.keys.contains(key) && !mainConfig.keys.contains(key)
        } ++ MANDATORY_DOMAIN_KEYS.filter(!configuration.keys.contains(_))

        // more checks
        val domains = configuration.underlying.getStringList("domains").asScala
        val missingDomains = if (domains.isEmpty) {
          Seq("domains")
        } else {
          Seq.empty
        }

        val missing = missingMandatory ++ missingDomains

        if (!missing.isEmpty) {
          Left((configurationKey -> missing))
        } else {
          try {
            Right(buildConfiguration(configurationKey, configuration))
          } catch {
            case t: Throwable =>
              log.error(s"Error while building configuration for organizations $configurationKey", t)
              Left((configurationKey -> Seq(t.getMessage)))
          }
        }

      }.toSeq

      val (naughty, nice) = parsedConfigurations.partition(_.isLeft)

      if (!naughty.isEmpty) {
        val errorPairs = naughty.map(_.left.get)
        log.error(
          """Invalid configuration(s):
          |%s
        """.stripMargin.format(
            errorPairs.map { config =>
              config
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
        allErrors ++= errorPairs.map(pair => (pair._1 -> ("Missing mandatory keys or other problem: " + pair._2.mkString(", "))))
      }

      val configurations = nice.map(_.right.get)

      val duplicateOrgIds = configurations.groupBy(_.orgId).filter(_._2.size > 1)
      if (!duplicateOrgIds.isEmpty) {
        log.error(
          "Found domain configurations that use the same orgId: " +
            duplicateOrgIds.map(t => t._1 + ": " + t._2.map(_.orgId).mkString(", ")).mkString(", "))

        allErrors ++= duplicateOrgIds.map(duplicate => (duplicate._1, s"Configuration with key ${duplicate._1} is present more than once, second definition dropped"))
      }

      // plugin time! now that we read all the configuration, enrich it with things provided by the plugins

      if (!Play.isTest) {
        //        val invalidPluginKeys: Seq[(OrganizationConfiguration, String, Option[CultureHubPlugin])] = configurations.flatMap { configuration =>
        //          configuration.plugins.map(key => Tuple3(configuration, key, plugins.find(_.pluginKey == key))).filter(_._3.isEmpty)
        val invalidPluginKeys = Seq.empty[(OrganizationConfiguration, String, Option[CultureHubPlugin])]

        if (!invalidPluginKeys.isEmpty) {

          val error = "Found two or more configurations that reference non-existing plugins:\n" +
            invalidPluginKeys.map(r => "Configuration " + r._1.orgId + ": plugin " + r._2 + " does not exist or is not available").mkString("\n")

          log.error(error)

          allErrors ++= invalidPluginKeys.map(invalid => (invalid._1.orgId -> s"Plugin ${invalid._2} does not exist"))
        }
      }

      // now we're good to go...

      val enhancedConfigurations = configurations.map { configuration =>
        val pluginRoles: Seq[Role] = configuration.plugins.flatMap(key => plugins.find(_.pluginKey == key)).flatMap(_.roles)

        val duplicateRolesDefinition = configuration.roles.filter(configRole => pluginRoles.exists(_.key == configRole.key))
        if (!duplicateRolesDefinition.isEmpty) {
          log.error(
            "Configuration %s defines role(s) that are already provided via plugins: %s".format(
              configuration.orgId, duplicateRolesDefinition.map(_.key).mkString(", ")
            )
          )
          allErrors += (configuration.orgId ->
            ("Duplicate role definition, role already provided via plugin: " + duplicateRolesDefinition.map(_.key).mkString(", "))
          )
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

      val pluginConfigurations: Map[(String, OrganizationConfiguration), Option[Configuration]] = configs.flatMap { configuration =>
        configuration.plugins.map { pluginKey =>
          ((pluginKey -> configuration) -> config.getConfig("%s.plugin.%s".format(configuration.orgId, pluginKey)))
        }
      }.toMap

      log.debug("Found following plugin configurations: " + pluginConfigurations.keys.map(_._1).mkString(", "))

      val groupedPluginConfigurations: Map[String, Map[OrganizationConfiguration, Option[Configuration]]] = pluginConfigurations.groupBy(_._1._1).map { g =>
        (g._1 -> {
          g._2.map(group => (group._1._2 -> group._2))
        })
      }.toMap

      groupedPluginConfigurations.foreach { pluginConfig =>
        CultureHubPlugin.hubPlugins.find(_.pluginKey == pluginConfig._1).map { plugin =>
          log.trace(s"Loading configuration for plugin ${plugin.pluginKey}")
          try {
            plugin.onBuildConfiguration(pluginConfig._2)
          } catch {
            case t: Throwable =>
              log.error(s"Could not configure plugin ${plugin.pluginKey}", t)
          }
        }
      }

      (configs, allErrors.toSeq)

    }

    private def buildConfiguration(configurationKey: String, configuration: Configuration) = OrganizationConfiguration(
      orgId = configuration.getString(ORG_ID).get,
      domains = configuration.underlying.getStringList("domains").asScala.toList,
      instances = configuration.underlying.getStringList(INSTANCES).asScala.toList,
      isReadOnly = configuration.getBoolean(READ_ONLY).getOrElse(false),
      mongoDatabase = configuration.getString(MONGO_DATABASE).get,
      baseXConfiguration = BaseXConfiguration(
        host = getString(configuration, BASEX_HOST),
        port = getInt(configuration, BASEX_PORT),
        eport = getInt(configuration, BASEX_EPORT),
        user = getString(configuration, BASEX_USER),
        password = getString(configuration, BASEX_PASSWORD)
      ),
      solrBaseUrl = getString(configuration, SOLR_BASE_URL),
      solrIndexerUrl = getOptionalString(configuration, SOLR_INDEXER_URL),
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
        tilesOutputBaseDir = getString(configuration, TILES_OUTPUT_DIR),
        graphicsMagickCommand = getString(configuration, GM_COMMAND)
      ),
      directoryService = DirectoryServiceConfiguration(
        providerDirectoryUrl = configuration.getString(PROVIDER_DIRECTORY_URL).getOrElse("")
      ),
      searchService = SearchServiceConfiguration(
        hiddenQueryFilter = getOptionalString(configuration, SEARCH_HQF).getOrElse(""),
        facets = getString(configuration, SEARCH_FACETS),
        sortFields = getString(configuration, SEARCH_SORTFIELDS),
        apiWsKeyEnabled = getOptionalBoolean(configuration, SEARCH_APIWSKEYENABLED).getOrElse(false),
        apiWsKeys = getOptionalStringList(configuration, SEARCH_APIWSKEYS).getOrElse(Seq.empty),
        moreLikeThis = {
          val mlt = configuration.getConfig(SEARCH_MORELIKETHIS)
          val default = MoreLikeThis()
          if (mlt.isEmpty) {
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
        rowLimit = getOptionalInt(configuration, SEARCH_ROW_LIMIT).getOrElse(500),
        showResultsWithoutThumbnails = getOptionalBoolean(configuration, SHOW_ITEMS_WITHOUT_THUMBNAIL).getOrElse(false)
      ),
      processingService = ProcessingServiceConfiguration(
        mappingCpuProportion = if (configuration.underlying.hasPath(PROCESSING_MAPPING_CPU_PROPORTION)) configuration.underlying.getDouble(PROCESSING_MAPPING_CPU_PROPORTION) else 0.5
      ),
      quotas = {
        configuration.getConfig("quotas").map { quotas =>
          quotas.subKeys.map { key =>
            (key -> quotas.getInt(key + ".limit").getOrElse(-1))
          }.toMap
        }.getOrElse {
          Map.empty
        }
      },
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
        showRegistration = configuration.getBoolean("ui.showRegistration").getOrElse(false),
        showAllObjects = configuration.getBoolean("ui.showAllObjects").getOrElse(false)
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
        roles =>
          roles.keys.map {
            key =>
              {
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

    private def getString(configuration: Configuration, key: String): String =
      configuration.getString(key).getOrElse(Play.configuration.getString(key).get)

    private def getOptionalString(configuration: Configuration, key: String): Option[String] =
      configuration.getString(key).orElse(Play.configuration.getString(key))

    private def getOptionalStringList(configuration: Configuration, key: String): Option[Seq[String]] =
      if (configuration.underlying.hasPath(key))
        Some(configuration.underlying.getStringList(key).asScala.toSeq)
      else
        None

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