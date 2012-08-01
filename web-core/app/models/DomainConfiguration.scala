package models {

import org.apache.solr.client.solrj.SolrQuery
import core.search.{SolrSortElement, SolrFacetElement}
import play.api.{Configuration, Play, Logger}
import Play.current
import collection.JavaConverters._

/**
 * Holds configuration that is used when a specific domain is accessed. It overrides a default configuration.
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
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

  // ~~~ schema
  schemas:                     Seq[String],
  crossWalks:                  Seq[String],

  // ~~~ user interface
  ui:                          UserInterfaceConfiguration,

  // ~~~ access control
  roles:                       Seq[Role],

  // ~~~ search
  hiddenQueryFilter:           Option[String] = Some(""),
  facets:                      Option[String] = None, // dc_creator:crea:Creator,dc_type
  sortFields:                  Option[String] = None, // dc_creator,dc_provider:desc
  apiWsKey:                    Boolean = false

) {

  def getFacets: List[SolrFacetElement] = {
    facets.getOrElse("").split(",").filter(k => k.split(":").size > 0 && k.split(":").size < 4).map {
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
    sortFields.getOrElse("").split(",").filter(sf => sf.split(":").size > 0 && sf.split(":").size < 3).map {
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
  homePage:                    Option[String] = None
)

case class CommonsServiceConfiguration(
  commonsHost:                 String,
  nodeName:                    String, // TODO deprecate this. We keep it for now to ease migration
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
  repositoryName: String,
  adminEmail: String,
  earliestDateStamp: String,
  repositoryIdentifier: String,
  sampleIdentifier: String,
  responseListSize: Int
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

  val COMMONS_HOST = "commons.host"
  val COMMONS_NODE_NAME = "commons.nodeName"
  val COMMONS_API_TOKEN = "commons.apiToken"

  val FILESTORE_DATABASE = "fileStoreDatabase"
  val IMAGE_CACHE_DATABASE = "imageCacheDatabase"
  val IMAGE_CACHE_ENABLED = "imageCacheEnabled"
  val TILES_WORKING_DIR = "tilesWorkingBaseDir"
  val TILES_OUTPUT_DIR = "tilesOutputBaseDir"

  val SCHEMAS = "schemas"
  val CROSSWALKS = "crossWalks"

  val OAI_REPO_NAME = "services.pmh.repositoryName"
  val OAI_ADMIN_EMAIL = "services.pmh.adminEmail"
  val OAI_EARLIEST_TIMESTAMP = "services.pmh.earliestDateStamp"
  val OAI_REPO_IDENTIFIER = "services.pmh.repositoryIdentifier"
  val OAI_SAMPLE_IDENTIFIER = "services.pmh.sampleIdentifier"
  val OAI_RESPONSE_LIST_SIZE = "services.pmh.responseListSize"

  val BASEX_HOST = "basex.host"
  val BASEX_PORT = "basex.port"
  val BASEX_EPORT = "basex.eport"
  val BASEX_USER = "basex.user"
  val BASEX_PASSWORD = "basex.password"

  val MANDATORY_OVERRIDABLE_KEYS = Seq(
    SOLR_BASE_URL,
    COMMONS_HOST, COMMONS_NODE_NAME,
    IMAGE_CACHE_DATABASE, FILESTORE_DATABASE, TILES_WORKING_DIR, TILES_OUTPUT_DIR,
    OAI_REPO_NAME, OAI_ADMIN_EMAIL, OAI_EARLIEST_TIMESTAMP, OAI_REPO_IDENTIFIER, OAI_SAMPLE_IDENTIFIER, OAI_RESPONSE_LIST_SIZE,
    BASEX_HOST, BASEX_PORT, BASEX_EPORT, BASEX_USER, BASEX_PASSWORD
  )
  val MANDATORY_DOMAIN_KEYS = Seq(ORG_ID, MONGO_DATABASE, COMMONS_API_TOKEN, IMAGE_CACHE_ENABLED, SCHEMAS, CROSSWALKS)


  /**
   * Computes all domain configurations based on the default Play configuration mechanism.
   */
  def getAll = {

    var missingKeys = new collection.mutable.HashMap[String, Seq[String]]

    val config = Play.configuration.getConfig("configurations").get
      val allDomainConfigurations = config.keys.filterNot(_.indexOf(".") < 0).map(_.split("\\.").head).toList.distinct
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
                  responseListSize = getInt(configuration, OAI_RESPONSE_LIST_SIZE)
                ),
                objectService = ObjectServiceConfiguration(
                  fileStoreDatabaseName = getString(configuration, FILESTORE_DATABASE),
                  imageCacheDatabaseName = getString(configuration, IMAGE_CACHE_DATABASE),
                  imageCacheEnabled = configuration.getBoolean(IMAGE_CACHE_ENABLED).getOrElse(false),
                  tilesWorkingBaseDir = getString(configuration, TILES_WORKING_DIR),
                  tilesOutputBaseDir = getString(configuration, TILES_OUTPUT_DIR)
                ),
                schemas = configuration.underlying.getStringList(SCHEMAS).asScala.toList,
                crossWalks = configuration.underlying.getStringList(CROSSWALKS).asScala.toList,
                ui = UserInterfaceConfiguration(
                  themeDir = configuration.getString("themeDir").getOrElse("default"),
                  defaultLanguage = configuration.getString("defaultLanguage").getOrElse("en"),
                  siteName = configuration.getString("siteName"),
                  siteSlogan = configuration.getString("siteSlogan").orElse(Some("Delving CultureHub"))
                ),
                emailTarget = {
                  val emailTarget = configuration.getConfig("emailTarget").get
                  EmailTarget(
                    adminTo = emailTarget.getString("adminTo").getOrElse("servers@delving.eu"),
                    exceptionTo = emailTarget.getString("exceptionTo").getOrElse("servers@delving.eu"),
                    feedbackTo = emailTarget.getString("feedbackTo").getOrElse("servers@delving.eu"),
                    registerTo = emailTarget.getString("registerTo").getOrElse("servers@delving.eu"),
                    systemFrom = emailTarget.getString("systemFrom").getOrElse("servers@delving.eu"),
                    feedbackFrom = emailTarget.getString("feedbackFrom").getOrElse("servers@delving.eu")
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
                hiddenQueryFilter = configuration.getString("hiddenQueryFilter"),
                facets = configuration.getString("facets"),
                sortFields = configuration.getString("sortFields"),
                apiWsKey = configuration.getBoolean("apiWsKey").getOrElse(false)
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

    if (Play.isTest) {
      configurations.map { c =>
        c.copy(
          mongoDatabase = c.mongoDatabase + "-TEST",
          objectService = c.objectService.copy(
            fileStoreDatabaseName = c.objectService.fileStoreDatabaseName + "-TEST",
            imageCacheDatabaseName = c.objectService.imageCacheDatabaseName + "-TEST"
          )
        )
      }
    } else {
      configurations
    }
  }


  private def getString(configuration: Configuration, key: String): String =
    configuration.getString(key).getOrElse(Play.configuration.getString(key).get)

  private def getInt(configuration: Configuration, key: String): Int =
    configuration.getInt(key).getOrElse(Play.configuration.getInt(key).get)

}

}