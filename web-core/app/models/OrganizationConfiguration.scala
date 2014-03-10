package models {

  import _root_.core.node.Node
  import core.SystemField
  import core.search.{ SortElement, FacetElement }
  import play.api.Logger

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
    metaTitle: Option[String],
    metaDescription: Option[String],
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

}