package plugins

import services.{ DataSetLookupService, MetadataRecordResolverService }
import jobs._
import play.api.{ Play, Application }
import Play.current
import models._
import processing.{ ProcessDataSetCollection, DataSetCollectionProcessor }
import util.OrganizationConfigurationHandler
import java.util.zip.GZIPInputStream
import com.mongodb.BasicDBObject
import io.Source
import akka.actor._
import akka.routing._
import akka.actor.SupervisorStrategy._
import scala.collection.JavaConverters._
import core._
import core.access.{ ResourceType, Resource, ResourceLookup }
import com.mongodb.casbah.commons.MongoDBObject
import java.util.regex.Pattern
import java.io.FileInputStream
import controllers.organization.DataSetEventFeed
import controllers.dataset.{ SipCreatorEndPointHelper, ReceiveSource, SipCreatorEndPoint }

class DataSetPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "dataSet"

  private val dataSetHarvestCollectionLookup = new DataSetLookupService

  // only for testing...
  private var testDataProcessor: ActorRef = null

  val schemaService: SchemaService = HubModule.inject[SchemaService](name = None)
  lazy val indexingServiceLocator = HubModule.inject[DomainServiceLocator[IndexingService]](name = None)

  /**
   * Override this to add menu entries to the organization menu
   * @param lang the active language
   * @param roles the roles of the current user
   * @return a sequence of [[core.MainMenuEntry]] for the organization menu
   */
  override def organizationMenuEntries(configuration: OrganizationConfiguration, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "datasets",
      titleKey = "dataset.Datasets",
      items = Seq(
        MenuElement("/admin/dataset", "dataset.DatasetList"),
        MenuElement("/admin/dataset/add", "dataset.CreateADataset", Seq(Role.OWN, DataSetPlugin.ROLE_DATASET_ADMIN))
      )
    )
  )

  override def services: Seq[Any] = Seq(
    new MetadataRecordResolverService,
    dataSetHarvestCollectionLookup
  )

  /**
   * Override this to provide custom roles to the platform, that can be used in Groups
   * @return a sequence of [[models.Role]] instances
   */
  override def roles: Seq[Role] = Seq(DataSetPlugin.ROLE_DATASET_ADMIN, DataSetPlugin.ROLE_DATASET_EDITOR)

  /**
   * Override this to provide the necessary lookup for a [[core.access.Resource]] depicted by a [[models.Role]]
   * @return
   */
  override val resourceLookups: Seq[core.access.ResourceLookup] = Seq(
    new ResourceLookup {

      def resourceType: ResourceType = DataSet.RESOURCE_TYPE

      /**
       * The total number of resources of this type
       * @return the number of resources for this ResourceType
       */
      def totalResourceCount(implicit configuration: OrganizationConfiguration): Int = {
        DataSet.dao.count().toInt
      }

      /**
       * Queries resources by type and name
       * @param query the query on the resource name
       * @return a sequence of resources matching the query
       */
      def findResources(orgId: String, query: String): Seq[Resource] = {
        implicit val configuration = OrganizationConfigurationHandler.getByOrgId(orgId)
        DataSet.dao.find(MongoDBObject(
          "orgId" -> orgId,
          "spec" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE))
        ).toSeq
      }

      /**
       * Queries resources by key
       * @param orgId the orgId
       * @param resourceKey the resourceKey
       * @return the resource of the given key, if found
       */
      def findResourceByKey(orgId: String, resourceKey: String): Option[Resource] = {
        implicit val configuration = OrganizationConfigurationHandler.getByOrgId(orgId)
        DataSet.dao.findOne(MongoDBObject("orgId" -> orgId, "spec" -> resourceKey))
      }
    }
  )

  /**
   * Called at actor initialization time. Plugins that make use of the ActorSystem should initialize their actors here
   * @param context the [[ ActorContext ]] for this plugin
   */
  override def onActorInitialization(context: ActorContext) {
    // DataSet source parsing
    context.actorOf(
      Props[ReceiveSource].withRouter(
        RoundRobinRouter(Runtime.getRuntime.availableProcessors(), supervisorStrategy = OneForOneStrategy() {
          case _ => Restart
        })
      ), name = "dataSetParser"
    )

    // DataSet processing
    context.actorOf(Props[Processor].withRouter(
      RoundRobinRouter(2, supervisorStrategy = OneForOneStrategy() {
        case _ => Restart
      })
    ), name = "dataSetProcessor")

    // Processing queue watcher
    context.actorOf(Props[ProcessingQueueWatcher])

    // DataSet event log housekeeping
    context.actorOf(Props[DataSetEventHousekeeper])

    // DataSet event feed
    context.actorOf(Props[DataSetEventFeed], name = "dataSetEventFeed")

    // only for testing
    testDataProcessor = context.actorOf(Props[DataSetCollectionProcessor])

  }

  /**
   * Runs globally on application start, for the whole Hub. Make sure that whatever you run here is multitenancy-aware
   */
  override def onStart() {

    checkSchemas()

    // ~~~ cleanup set states

    if (!Play.isTest) {
      val instanceIdentifier = Play.current.configuration.getString("cultureHub.instanceIdentifier").getOrElse("default")
      DataSet.all.foreach { dataSetDAO =>
        dataSetDAO.findByState(DataSetState.PROCESSING, DataSetState.CANCELLED, DataSetState.PROCESSING_QUEUED).
          filter(_.processingInstanceIdentifier == Some(instanceIdentifier)).
          foreach { set =>
            dataSetDAO.updateState(set, DataSetState.CANCELLED)
            try {
              implicit val configuration = OrganizationConfigurationHandler.getByOrgId(set.orgId)
              indexingServiceLocator.byDomain.deleteBySpec(set.orgId, set.spec)
            } catch {
              case t: Throwable => error(
                "Couldn't delete SOLR index for cancelled set %s:%s at startup".format(
                  set.orgId,
                  set.spec
                ),
                t
              )
            } finally {
              dataSetDAO.updateState(set, DataSetState.UPLOADED)
            }
          }
      }
    }
  }

  /**
   *   check if we have access to all schemas that are used by the DataSets
   */
  private def checkSchemas() {

    val schemasInUse: Map[String, Seq[String]] = DataSet.all.flatMap { dao =>
      dao.findAll().flatMap { set =>
        set.mappings.values.map { mapping =>
          (mapping.schemaPrefix -> mapping.schemaVersion)
        }
      }
    }.foldLeft(Map.empty[String, Seq[String]]) { (acc, pair) =>
      acc + (pair._1 -> Seq(pair._2))
    } - "raw" // boldly ignore the raw schema, which is fake.

    val allSchemas = schemaService.getAllSchemas

    val missingVersions: Map[String, Seq[String]] = schemasInUse.map(inUse => {

      val availableVersionNumbers = allSchemas.find(_.prefix == inUse._1).map { schema =>
        schema.versions.asScala.map(_.number)
      }.getOrElse(Seq.empty)

      val intersection = availableVersionNumbers.intersect(inUse._2)

      (inUse._1 -> inUse._2.filterNot(intersection.contains(_)))
    })

    if (missingVersions.exists(missing => !missing._2.isEmpty)) {
      error(
        """
          |The SchemaRepository does not provide some of the versions in use by the stored DataSets. Fix this before starting the hub!
          |
          |Affected schemas / versions:
          |
          |%s
        """.stripMargin.format(
          missingVersions.map(missing =>
            "%s -> %s".format(
              missing._1, missing._2.map("'%s'".format(_)).mkString(", ")
            )
          ).mkString("\n")
        ))

      throw new RuntimeException("Cannot start the hub due to missing schemas")
    }

  }

  override def onStop() {
    if (!Play.isTest) {
      // cleanly cancel all active processing
      DataSet.all.foreach {
        dataSetDAO =>
          dataSetDAO.findByState(DataSetState.PROCESSING).foreach { set =>
            dataSetDAO.updateState(set, DataSetState.CANCELLED)
          }
          dataSetDAO.findByState(DataSetState.PROCESSING_QUEUED).foreach { set =>
            dataSetDAO.updateState(set, DataSetState.UPLOADED)
          }
      }
      Thread.sleep(2000)
    }
  }

  /**
   * Executed when test data is loaded (for development and testing)
   */
  override def onLoadTestData(parameters: Map[String, Seq[String]]) {
    val samples = parameters.get("samples").getOrElse(Seq.empty)
    val now = System.currentTimeMillis()

    BootstrapSource.sources.
      filter(s => samples.contains(s.spec)).
      foreach(boot => bootstrapDataset(boot))

    println()
    println("** Loaded DataSet plugin test data for sample(s) %s in %s ms **".format(
      samples.mkString(", "), System.currentTimeMillis() - now)
    )
    println()
  }

  def bootstrapDataset(boot: BootstrapSource) {
    if (DataSet.dao(boot.org).count(MongoDBObject("spec" -> boot.spec)) == 0) {

      implicit val configuration = OrganizationConfigurationHandler.getByOrgId(("delving"))

      val factMap = new BasicDBObject()
      factMap.put("spec", boot.spec)
      factMap.put("name", boot.spec)
      factMap.put("collectionType", "all")
      factMap.put("namespacePrefix", "raw")
      factMap.put("language", "nl")
      factMap.put("country", "netherlands")
      factMap.put("provider", "Sample Man")
      factMap.put("dataProvider", "Sample Man")
      factMap.put("rights", "http://creativecommons.org/publicdomain/mark/1.0/")
      factMap.put("type", "IMAGE")

      val mappingFile = boot.file("mapping_icn.xml")

      DataSet.dao("delving").insert(DataSet(
        spec = boot.spec,
        userName = "bob",
        orgId = "delving",
        state = DataSetState.ENABLED,
        deleted = false,
        details = Details(
          name = boot.spec,
          facts = factMap
        ),
        idxMappings = List("icn"),
        invalidRecords = Map("icn" -> List(1)),
        mappings = Map("icn" -> Mapping(
          schemaPrefix = "icn",
          schemaVersion = "1.0.0", // TODO
          recordMapping = Some(Source.fromInputStream(new FileInputStream(mappingFile)).getLines().mkString("\n"))
        )),
        formatAccessControl = Map("icn" -> FormatAccessControl(accessType = "public"))
      ))

      val dataSet = DataSet.dao.findBySpecAndOrgId(boot.spec, boot.org).get

      // provision records, but only if necessary
      if (HubServices.basexStorages.getResource(configuration).openCollection(dataSet).isEmpty) {
        val sourceFile = boot.file("source.xml.gz")
        SipCreatorEndPointHelper.loadSourceData(
          dataSet,
          new GZIPInputStream(new FileInputStream(sourceFile))
        )
        while (DataSet.dao.getState(dataSet.orgId, dataSet.spec) == DataSetState.PARSING) Thread.sleep(100)
      }

      if (Play.isTest) {
        val dataSet = DataSet.dao.findBySpecAndOrgId(boot.spec, boot.org).get

        DataSet.dao.updateState(dataSet, DataSetState.QUEUED)
        var processing = true
        testDataProcessor ! ProcessDataSetCollection(
          set = dataSet,
          onSuccess = { () =>
            DataSet.dao.updateProcessingInstanceIdentifier(dataSet, None)
            DataSet.dao.updateState(dataSet, DataSetState.ENABLED)
            processing = false
          },
          onFailure = { t =>
            DataSet.dao.updateProcessingInstanceIdentifier(dataSet, None)
            DataSet.dao.updateState(dataSet, DataSetState.ERROR, errorMessage = Some(t.getMessage))
            processing = false
          },
          configuration
        )

        while (processing) {
          // we need to block because otherwise the test aren't prepared correctly
          // of course something that would simulate and generate processing data would be the proper way to go here
          Thread.sleep(500)
        }
      }

      boot.init()
    }

  }
}

object DataSetPlugin {

  lazy val ROLE_DATASET_ADMIN = Role(
    key = "dataSetAdmin",
    description = Map("en" -> "Dataset administration rights", "sv" -> "Adminstrationsrättigheter för dataset"),
    isResourceAdmin = true,
    resourceType = Some(DataSet.RESOURCE_TYPE)
  )

  lazy val ROLE_DATASET_EDITOR = Role(
    key = "dataSetEditor",
    description = Map("en" -> "Dataset modification rights", "sv" -> "Dataset redigeringsrättigheter"),
    isResourceAdmin = false,
    resourceType = Some(DataSet.RESOURCE_TYPE)
  )

  val ITEM_TYPE = ItemType("mdr")

}