package plugins

import jobs._
import play.api.{Logger, Play, Application}
import Play.current
import models._
import processing.DataSetCollectionProcessor
import util.DomainConfigurationHandler
import java.util.zip.GZIPInputStream
import com.mongodb.BasicDBObject
import io.Source
import play.api.libs.concurrent.Akka
import akka.actor.{OneForOneStrategy, Props}
import akka.routing.RoundRobinRouter
import akka.actor.SupervisorStrategy.Restart
import controllers.{organization, ReceiveSource}
import core.indexing.IndexingService
import scala.collection.immutable.ListMap
import scala.util.matching.Regex
import play.api.mvc.Handler
import core._
import access.{ResourceType, Resource, ResourceLookup}
import collection.HarvestCollectionLookup
import com.mongodb.casbah.commons.MongoDBObject
import java.util.regex.Pattern

class DataSetPlugin(app: Application) extends CultureHubPlugin(app) {

  val pluginKey: String = "dataSet"

  private val dataSetHarvestCollectionLookup = new DataSetLookup

  /**

  GET         /:user/sip-creator.jnlp                                           controllers.organization.SipCreator.jnlp(user)

  GET         /organizations/:orgId/dataset                                     controllers.organization.DataSets.list(orgId)
  GET         /organizations/:orgId/dataset/feed                                controllers.organization.DataSets.feed(orgId, clientId: String, spec: Option[String])
  GET         /organizations/:orgId/dataset/add                                 controllers.organization.DataSetControl.dataSet(orgId, spec: Option[String] = None)
  GET         /organizations/:orgId/dataset/:spec/update                        controllers.organization.DataSetControl.dataSet(orgId, spec: Option[String])
  POST        /organizations/:orgId/dataset/submit                              controllers.organization.DataSetControl.dataSetSubmit(orgId)
  GET         /organizations/:orgId/dataset/:spec                               controllers.organization.DataSets.dataSet(orgId, spec)

  GET         /organizations/:orgId/sip-creator                                 controllers.organization.SipCreator.index(orgId)

  GET         /api/sip-creator/list                                             controllers.SipCreatorEndPoint.listAll(accessKey: Option[String] ?= None)
  GET         /api/sip-creator/unlock/:orgId/:spec                              controllers.SipCreatorEndPoint.unlock(orgId, spec, accessKey: Option[String] ?= None)
  POST        /api/sip-creator/submit/:orgId/:spec                              controllers.SipCreatorEndPoint.acceptFileList(orgId, spec, accessKey: Option[String] ?= None)
  POST        /api/sip-creator/submit/:orgId/:spec/:fileName                    controllers.SipCreatorEndPoint.acceptFile(orgId, spec, fileName, accessKey: Option[String] ?= None)
  GET         /api/sip-creator/fetch/:orgId/:spec-sip.zip                       controllers.SipCreatorEndPoint.fetchSIP(orgId, spec, accessKey: Option[String] ?= None)

   */

  override val routes: ListMap[(String, Regex), (List[String], Map[String, String]) => Handler] = ListMap(

    ("GET", """^/([A-Za-z0-9-]+)/sip-creator.jnlp""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => organization.SipCreator.jnlp(pathArgs(0))
    },

    ("GET", """^/organizations/([A-Za-z0-9-]+)/dataset""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.DataSets.list(pathArgs(0))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/dataset/feed""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.DataSets.feed(pathArgs(0), queryString("clientId"), queryString.get("spec"))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/dataset/add""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.DataSetControl.dataSet(pathArgs(0), None)
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/dataset/([A-Za-z0-9-]+)/update""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.DataSetControl.dataSet(pathArgs(0), Some(pathArgs(1)))
    },
    ("POST", """^/organizations/([A-Za-z0-9-]+)/dataset/submit""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.DataSetControl.dataSetSubmit(pathArgs(0))
    },
    ("GET", """^/organizations/([A-Za-z0-9-]+)/dataset/([A-Za-z0-9-]+)""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.organization.DataSets.dataSet(pathArgs(0), pathArgs(1))
    },

    ("GET", """^/organizations/([A-Za-z0-9-]+)/sip-creator""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => organization.SipCreator.index(pathArgs(0))
    },

    ("GET", """^/api/sip-creator/list""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.SipCreatorEndPoint.listAll(queryString.get("accessKey"))
    },
    ("GET", """^/api/sip-creator/unlock/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.SipCreatorEndPoint.unlock(pathArgs(0), pathArgs(1), queryString.get("accessKey"))
    },
    ("POST", """^/api/sip-creator/submit/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)/(.*)""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.SipCreatorEndPoint.acceptFile(pathArgs(0), pathArgs(1), pathArgs(2), queryString.get("accessKey"))
    },
    ("POST", """^/api/sip-creator/submit/([A-Za-z0-9-]+)/(.*)""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.SipCreatorEndPoint.acceptFileList(pathArgs(0), pathArgs(1), queryString.get("accessKey"))
    },
    ("GET", """^/api/sip-creator/fetch/([A-Za-z0-9-]+)/([A-Za-z0-9-]+)-sip.zip""".r) -> {
      (pathArgs: List[String], queryString: Map[String, String]) => controllers.SipCreatorEndPoint.fetchSIP(pathArgs(0), pathArgs(1), queryString.get("accessKey"))
    }

  )

  /**
   * Override this to add menu entries to the organization menu
   * @param orgId the organization ID
   * @param lang the active language
   * @param roles the roles of the current user
   * @return a sequence of [[core.MainMenuEntry]] for the organization menu
   */
  override def organizationMenuEntries(orgId: String, lang: String, roles: Seq[String]): Seq[MainMenuEntry] = Seq(
    MainMenuEntry(
      key = "datasets",
      titleKey = "thing.datasets",
      items = Seq(
        MenuElement("/organizations/%s/dataset".format(orgId), "organization.dataset.list"),
        MenuElement("/organizations/%s/dataset/add".format(orgId), "organization.dataset.create", Seq(Role.OWN))
      )
    ),
    MainMenuEntry(
      key = "sipcreator",
      titleKey = "ui.label.sipcreator",
      mainEntry = Some(MenuElement("/organizations/%s/sip-creator".format(orgId), "ui.label.sipcreator"))
    )

  )

  override def harvestCollectionLookups: Seq[HarvestCollectionLookup] = Seq(dataSetHarvestCollectionLookup)

  /**
   * Override this to provide custom roles to the platform, that can be used in Groups
   * @return a sequence of [[models.Role]] instances
   */
  override val roles: Seq[Role] = Seq(DataSetPlugin.ROLE_DATASET_ADMIN, DataSetPlugin.ROLE_DATASET_EDITOR)

  /**
   * Override this to provide the necessary lookup for a [[core.access.Resource]] depicted by a [[models.Role]]
   * @return
   **/
  override val resourceLookups: Seq[core.access.ResourceLookup] = Seq(
    new ResourceLookup {

      def resourceType: ResourceType = DataSet.RESOURCE_TYPE

      /**
       * Queries resources by type and name
       * @param query the query on the resource name
       * @return a sequence of resources matching the query
       */
      def findResources(orgId: String, query: String): Seq[Resource] = {
        implicit val configuration = DomainConfigurationHandler.getByOrgId(orgId)
        DataSet.dao.find(MongoDBObject("orgId" -> orgId, "spec" -> Pattern.compile(query, Pattern.CASE_INSENSITIVE))).toSeq
      }

      /**
       * Queries resources by key
       * @param orgId the orgId
       * @param resourceKey the resourceKey
       * @return the resource of the given key, if found
       */
      def findResourceByKey(orgId: String, resourceKey: String): Option[Resource] = {
        implicit val configuration = DomainConfigurationHandler.getByOrgId(orgId)
        DataSet.dao.findOne(MongoDBObject("orgId" -> orgId, "spec" -> resourceKey))
      }
    }
  )



  /**
   * Runs globally on application start, for the whole Hub. Make sure that whatever you run here is multitenancy-aware
   */
  override def onStart() {

    // ~~~ jobs

    // DataSet source parsing
    Akka.system.actorOf(Props[ReceiveSource].withRouter(
      RoundRobinRouter(Runtime.getRuntime.availableProcessors(), supervisorStrategy = OneForOneStrategy() {
        case _ => Restart
      })
    ), name = "dataSetParser")

    // DataSet processing
    // Play can't do multi-threading in DEV mode...
    if (Play.isDev) {
      Akka.system.actorOf(Props[Processor], name = "dataSetProcessor")
    } else {
      Akka.system.actorOf(Props[Processor].withRouter(
        RoundRobinRouter(Runtime.getRuntime.availableProcessors(), supervisorStrategy = OneForOneStrategy() {
          case _ => Restart
        })
      ), name = "dataSetProcessor")
    }

    // Processing queue watcher
    Akka.system.actorOf(Props[ProcessingQueueWatcher])

    // DataSet event log housekeeping
    Akka.system.actorOf(Props[DataSetEventHousekeeper])

    // ~~~ cleanup set states

    if (!Play.isTest) {
      DataSet.all.foreach {
        dataSetDAO =>
          dataSetDAO.findByState(DataSetState.PROCESSING, DataSetState.CANCELLED).foreach {
            set =>
              dataSetDAO.updateState(set, DataSetState.CANCELLED)
              try {
                IndexingService.deleteBySpec(set.orgId, set.spec)(DomainConfigurationHandler.getByOrgId(set.orgId))
              } catch {
                case t: Throwable => Logger("CultureHub").error("Couldn't delete SOLR index for cancelled set %s:%s at startup".format(set.orgId, set.spec), t)
              } finally {
                dataSetDAO.updateState(set, DataSetState.UPLOADED)
              }
          }
      }
      Thread.sleep(3000)
    }
  }


  override def onStop() {
    if (!Play.isTest) {
      // cleanly cancel all active processing
      DataSet.all.foreach {
        dataSetDAO =>
          dataSetDAO.findByState(DataSetState.PROCESSING).foreach {
            set =>
              dataSetDAO.updateState(set, DataSetState.CANCELLED)
          }

      }
      Thread.sleep(2000)
    }
  }

  /**
   * Executed when test data is loaded (for development and testing)
   */
  override def onLoadTestData() {
    if (DataSet.dao("delving").count(MongoDBObject("spec" -> "PrincessehofSample")) == 0) bootstrapDatasets()

    implicit val configuration = DomainConfigurationHandler.getByOrgId(("delving"))
    val dataSet = DataSet.dao.findBySpecAndOrgId("PrincessehofSample", "delving").get

    // provision records, but only if necessary
    if (HubServices.basexStorage(configuration).openCollection(dataSet).isEmpty) {
      _root_.controllers.SipCreatorEndPoint.loadSourceData(dataSet, new GZIPInputStream(Play.application.resourceAsStream("/bootstrap/EA525DF3C26F760A1D744B7A63C67247__source.xml.gz").get))
      Thread.sleep(2000)
    }

    // index them
    DataSet.dao.updateState(dataSet, DataSetState.QUEUED)
    DataSetCollectionProcessor.process(dataSet)

    while (DataSet.dao.getState(dataSet.orgId, dataSet.spec) == DataSetState.PROCESSING) {
      Thread.sleep(1000)
    }

  }

  def bootstrapDatasets() {
    implicit val configuration = DomainConfigurationHandler.getByOrgId(("delving"))

    val factMap = new BasicDBObject()
    factMap.put("spec", "PrincesseofSample")
    factMap.put("name", "Princessehof Sample Dataset")
    factMap.put("collectionType", "all")
    factMap.put("namespacePrefix", "raw")
    factMap.put("language", "nl")
    factMap.put("country", "netherlands")
    factMap.put("provider", "Sample Man")
    factMap.put("dataProvider", "Sample Man")
    factMap.put("rights", "http://creativecommons.org/publicdomain/mark/1.0/")
    factMap.put("type", "IMAGE")

    DataSet.dao("delving").insert(DataSet(
      spec = "PrincessehofSample",
      userName = "bob",
      orgId = "delving",
      state = DataSetState.ENABLED,
      deleted = false,
      details = Details(
        name = "Princessehof Sample Dataset",
        facts = factMap
      ),
      idxMappings = List("icn"),
      invalidRecords = Map("icn" -> List(1)),
      mappings = Map("icn" -> Mapping(
        format = RecordDefinition.getRecordDefinition("icn").get,
        recordMapping = Some(Source.fromInputStream(Play.application.resource("/bootstrap/A2098A0036EAC14E798CA3B653B96DD5__mapping_icn.xml").get.openStream()).getLines().mkString("\n"))
      )),
      formatAccessControl = Map("icn" -> FormatAccessControl(accessType = "public"))
    ))
  }
}

object DataSetPlugin {
  val ROLE_DATASET_ADMIN = Role(
    key = "dataSetAdmin",
    description = Map("en" -> "DataSet administration rights"),
    isResourceAdmin = true,
    resourceType = Some(DataSet.RESOURCE_TYPE)
  )

  val ROLE_DATASET_EDITOR = Role(
    key = "dataSetEditor",
    description = Map("en" -> "DataSet modification rights"),
    isResourceAdmin = false,
    resourceType = Some(DataSet.RESOURCE_TYPE)
  )

}
