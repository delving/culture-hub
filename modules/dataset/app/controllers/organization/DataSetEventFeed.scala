package controllers.organization

import play.api.libs.concurrent.Execution.Implicits._
import util.OrganizationConfigurationHandler
import scala.concurrent.duration._
import akka.actor._
import akka.util.Timeout
import akka.pattern.ask
import play.api.libs.iteratee._
import play.api.Play.current
import models.{ OrganizationConfiguration, DataSetEventLog, DataSetState, DataSet, HubUser }
import play.api.Logger
import models.DataSetState._
import play.api.libs.json._
import scala.concurrent.Future
import play.api.libs.concurrent.Akka
import extensions.Email
import play.api.libs.json._
import core.{ HubModule, IndexingService, DomainServiceLocator }

/**
 * TODO access control
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSetEventFeed {

  val indexingServiceLocator: DomainServiceLocator[IndexingService] = HubModule.inject[DomainServiceLocator[IndexingService]](name = None)

  val log = Logger(getClass)

  implicit def dataSetToListViewModel(ds: DataSet): ListDataSetViewModel = ListDataSetViewModel(
    spec = ds.spec,
    orgId = ds.orgId,
    name = ds.getName,
    nodeId = "", // TODO once we have nodes...
    nodeName = ds.getDataProvider,
    totalRecords = ds.getTotalRecords,
    processedRecords = ds.details.processedRecordCount,
    validRecords = ds.details.invalidRecordCount.map(f => (f._1 -> (ds.getTotalRecords - f._2))),
    state = ds.state.name,
    lockState = if (ds.lockedBy.isDefined) "locked" else "unlocked",
    lockedBy = if (ds.lockedBy.isDefined) ds.lockedBy.get else "",
    harvestingConfiguration = ds.formatAccessControl.map(f => (f._1 -> f._2.accessType)).toSeq,
    canEdit = (ds.editors ++ ds.administrators).distinct
  )

  implicit def dataSetListToListViewModelList(dsl: Seq[DataSet]): Seq[ListDataSetViewModel] = dsl.map(ds => dataSetToListViewModel(ds))

  implicit def dataSetToViewModel(ds: DataSet): DataSetViewModel = DataSetViewModel(
    spec = ds.spec,
    orgId = ds.orgId,
    creator = ds.getCreator,
    name = ds.getName,
    description = ds.description.getOrElse(""),
    information = DataSetInformation(
      language = ds.getLanguage,
      country = ds.getCountry,
      dataProvider = ds.getDataProvider,
      rights = ds.getRights,
      `type` = ds.getType
    ),
    nodeId = "", // TODO once we have nodes...
    nodeName = ds.getDataProvider,
    totalRecords = ds.getTotalRecords,
    validRecords = ds.details.invalidRecordCount.map(f => (f._1 -> (ds.getTotalRecords - f._2))),
    processedRecords = ds.details.processedRecordCount,
    state = ds.state.name,
    lockState = if (ds.lockedBy.isDefined) "locked" else "unlocked",
    lockedBy = if (ds.lockedBy.isDefined) ds.lockedBy.get else "Nobody",
    harvestingConfiguration = ds.formatAccessControl.map(f => (f._1 -> f._2.accessType)).toSeq,
    indexingSchema = ds.getIndexingMappingPrefix.getOrElse(""),
    errorMessage = ds.errorMessage
  )

  implicit def dataSetListToViewModelList(dsl: Seq[DataSet]): Seq[DataSetViewModel] = dsl.map(dataSetToViewModel(_))

  def default = Akka.system.actorFor("akka://application/user/plugin-dataSet/dataSetEventFeed")

  def subscribe(orgId: String, clientId: String, userName: String, configuration: String, spec: Option[String]): Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {

    log.debug("Client %s of org %s requesting subscribtion to DataSetList feed".format(clientId, orgId))

    implicit val timeout = Timeout(1 second)

    (default ? Subscribe(orgId, userName, configuration, clientId, spec)).map {

      case Connected(enumerator) =>

        log.debug("Client %s connected to feed".format(clientId))

        val iteratee = Iteratee.foreach[JsValue] {
          event => //default ! ClientMessage(event)
        }.mapDone {
          _ =>
            log.debug("Unsubscribing %s from feed".format(clientId))
            default ! Unsubscribe(clientId)
        }

        (iteratee, enumerator)

      case CannotConnect(error) =>

        log.warn("Client %s could not connect to DataSet feed".format(clientId))

        // A finished Iteratee sending EOF
        val iteratee = Done[JsValue, Unit]((), Input.EOF)

        // Send an error and close the socket
        val enumerator = Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))

        (iteratee, enumerator)

    }
  }

  case class Subscribe(orgId: String, userName: String, configuration: String, clientId: String, spec: Option[String] = None)
  case class Unsubscribe(clientId: String)

  case class Connected(enumerator: Enumerator[JsValue])
  case class CannotConnect(msg: String)

  case object StartPolling
  case object StopPolling
  case object Update
  case object CheckForDeadClients

  case class ClientMessage(clientId: String, eventType: String, spec: String)

  case class ListDataSetViewModel(spec: String,
      orgId: String,
      name: String,
      nodeId: String,
      nodeName: String,
      totalRecords: Long,
      validRecords: Map[String, Long],
      processedRecords: Option[Long],
      state: String,
      lockState: String,
      lockedBy: String,
      harvestingConfiguration: Seq[(String, String)],
      canEdit: Seq[String]) {

    def toJson: JsObject = JsObject(
      Seq(
        "spec" -> JsString(spec),
        "orgId" -> JsString(orgId),
        "name" -> JsString(name),
        "nodeId" -> JsString(nodeId),
        "nodeName" -> JsString(nodeName),
        "totalRecords" -> JsNumber(totalRecords),
        "validRecords" -> JsArray(validRecords.toSeq.map(f => JsObject(Seq(("schema" -> JsString(f._1)), ("valid" -> JsNumber(f._2)))))),
        "processedRecords" -> JsString(processedRecords.getOrElse(0).toString),
        "dataSetState" -> JsString(state),
        "lockState" -> JsString(lockState),
        "lockedBy" -> JsString(lockedBy),
        "harvestingConfiguration" -> JsArray(harvestingConfiguration.toSeq.map(f => JsObject(Seq(("schema" -> JsString(f._1)), ("accessType" -> JsString(f._2)))))),
        "canEdit" -> JsArray(canEdit.map(JsString(_)))
      )
    )
  }

  case class DataSetViewModel(spec: String,
      orgId: String,
      creator: String,
      name: String,
      description: String,
      information: DataSetInformation,
      nodeId: String,
      nodeName: String,
      totalRecords: Long,
      validRecords: Map[String, Long],
      processedRecords: Option[Long],
      state: String,
      lockState: String,
      lockedBy: String,
      harvestingConfiguration: Seq[(String, String)],
      indexingSchema: String,
      errorMessage: Option[String]) {

    def toJson: JsObject = JsObject(
      Seq(
        "spec" -> JsString(spec),
        "orgId" -> JsString(orgId),
        "creator" -> JsString(creator),
        "name" -> JsString(name),
        "description" -> JsString(description),
        "information" -> information.toJson,
        "nodeId" -> JsString(nodeId),
        "nodeName" -> JsString(nodeName),
        "totalRecords" -> JsNumber(totalRecords),
        "validRecords" -> JsArray(validRecords.toSeq.map(f => JsObject(Seq(("schema" -> JsString(f._1)), ("valid" -> JsNumber(f._2)))))),
        "processedRecords" -> JsString(processedRecords.getOrElse(0).toString),
        "dataSetState" -> JsString(state),
        "lockState" -> JsString(lockState),
        "lockedBy" -> JsString(lockedBy),
        "harvestingConfiguration" -> JsArray(harvestingConfiguration.toSeq.map(f => JsObject(Seq(("schema" -> JsString(f._1)), ("accessType" -> JsString(f._2)))))),
        "indexingSchema" -> JsString(indexingSchema),
        "errorMessage" -> JsString(errorMessage.getOrElse(""))
      )
    )
  }

  case class DataSetInformation(language: String,
      country: String,
      dataProvider: String,
      rights: String,
      `type`: String) {

    def toJson: JsObject = JsObject(
      Seq(
        "language" -> JsString(language),
        "country" -> JsString(country),
        "dataProvider" -> JsString(dataProvider),
        "rights" -> JsString(rights),
        "type" -> JsString(`type`)
      )
    )
  }

}

class DataSetEventFeed extends Actor {

  import DataSetEventFeed._

  val log = Logger("CultureHub")

  var pollScheduler: Cancellable = null

  var deadClientCheckScheduler: Cancellable = null

  implicit val timeout = Timeout(1 second)

  val LIST_FEED_EVENTS = Seq(EventType.CREATED, EventType.UPDATED, EventType.REMOVED, EventType.STATE_CHANGED, EventType.LOCKED, EventType.UNLOCKED, EventType.PROCESSED_RECORD_COUNT_CHANGED)

  var lastSeen: Long = System.currentTimeMillis()

  var subscribers = Map.empty[String, Subscriber]

  var timedoutSubscribers = Map.empty[String, Int]

  def receive = {

    case Subscribe(orgId, userName, configuration, clientId, spec) => {
      // Create an Enumerator to write to this socket

      val (enumerator, channel) = Concurrent.broadcast[JsValue]

      if (subscribers.isEmpty) {
        // if there was no subscriber before, start the polling
        self ! StartPolling
      }
      val newSubscriber = Subscriber(orgId, userName, configuration, spec, enumerator, channel)
      if (subscribers.contains(clientId)) {
        // when pressing the back button on some browsers, this is what we get
        // TODO: if replacing client-side loading with something entirely in javascript, create new clientId at each page load
        subscribers = subscribers.updated(clientId, newSubscriber)
      } else {
        subscribers = subscribers + (clientId -> newSubscriber)
      }
      sender ! Connected(enumerator)

    }

    case Unsubscribe(clientId) =>
      subscribers = subscribers - clientId
      if (subscribers.isEmpty) {
        self ! StopPolling
      }

    case ClientMessage(clientId, eventType, spec) =>

      log.debug(s"Received message from client $clientId of type $eventType with spec $spec")

      subscribers.find(_._1 == clientId).map {
        subscriber =>
          {

            val s = subscriber._2
            val orgId = subscriber._2.orgId
            val userName = subscriber._2.userName
            implicit val configuration = OrganizationConfigurationHandler.getByOrgId(subscriber._2.configuration)

            def withAdministrableSet(block: DataSet => Unit) {
              withSet(block, (set, userName) => DataSet.dao.canAdministrate(userName))
            }

            def withEditableSet(block: DataSet => Unit) {
              withSet(block, (set, userName) => DataSet.dao.canEdit(set, userName))
            }

            def withSet(block: DataSet => Unit, checkAccess: (DataSet, String) => Boolean) {
              DataSet.dao.findBySpecAndOrgId(spec, orgId).map {
                set =>
                  {
                    if (configuration.isReadOnly) {
                      send(s, error("The system is in read-only mode and cannot process your request."))
                    } else if (!configuration.isReadOnly && checkAccess(set, userName)) {
                      block(set)
                    } else if (!configuration.isReadOnly) {
                      send(s, error("Sorry, you are not authorized to perform this action!"))
                    }
                  }
              }.getOrElse {
                send(s, error("DataSet %s not found".format(spec)))
              }
            }

            eventType match {

              case "sendList" =>
                log.debug("About to send complete list of sets to client " + clientId)
                val sets: Seq[ListDataSetViewModel] = DataSet.dao.findAllByOrgId(orgId)
                val jsonList = sets.map(_.toJson).toSeq
                val msg = JsObject(
                  Seq(
                    "eventType" -> JsString("loadList"),
                    "payload" -> JsArray(jsonList)
                  )
                )
                send(s, msg)

              case "sendSet" =>
                log.debug("About to send set %s to client %s".format(spec, clientId))
                val msg = DataSet.dao.findBySpecAndOrgId(spec, orgId).map {
                  ds =>
                    {
                      val set: DataSetViewModel = ds
                      val payload = set.toJson ++ JsObject(Seq("cannotEdit" -> JsBoolean(!DataSet.dao.canEdit(ds, userName))))
                      JsObject(
                        Seq(
                          "eventType" -> JsString("loadSet"),
                          "payload" -> payload
                        )
                      )
                    }
                }.getOrElse {
                  JsObject(
                    Seq(
                      "eventType" -> JsString("loadSet"),
                      "error" -> JsString("notFound")
                    )
                  )
                }
                send(s, msg)

              case "enableSet" =>
                withEditableSet {
                  set =>
                    {
                      set.state match {
                        case DISABLED =>
                          DataSet.dao.updateState(set, DataSetState.ENABLED, Some(userName))
                          send(s, ok)
                        case _ => send(s, error("Cannot enable set that is not disabled"))
                      }
                    }
                }

              case "disableSet" =>
                withEditableSet {
                  set =>
                    {
                      set.state match {
                        case ENABLED =>
                          try {
                            indexingServiceLocator.byDomain.deleteBySpec(set.orgId, set.spec)
                            DataSet.dao.updateState(set, DataSetState.DISABLED, Some(userName))
                            send(s, ok)
                          } catch {
                            case t: Throwable =>
                              log.warn("Error while trying to remove cancelled set from index", t)
                              DataSet.dao.updateState(set, DataSetState.ERROR, Some(userName), Some(t.getMessage))
                              send(s, error("Cannot disable set that is not enabled"))
                          }
                        case _ => send(s, error("Cannot disable set that is not enabled"))
                      }
                    }
                }

              case "processSet" =>
                withEditableSet {
                  set =>
                    {
                      set.state match {
                        case ENABLED | DISABLED | UPLOADED | ERROR =>
                          DataSet.dao.updateIndexingControlState(
                            dataSet = set,
                            mapping = set.getIndexingMappingPrefix.getOrElse(""),
                            facets = configuration.getFacets.map(_.facetName),
                            sortFields = configuration.getSortFields.map(_.sortKey)
                          )
                          DataSet.dao.updateState(set, DataSetState.QUEUED, Some(userName))
                          send(s, ok)
                        case _ => send(s, error("Cannot process set that is not enabled, disabled, uploaded or in error"))
                      }
                    }
                }

              case "cancelProcessSet" =>
                withEditableSet {
                  set =>
                    {
                      set.state match {
                        case QUEUED | PROCESSING =>
                          DataSet.dao.updateState(set, DataSetState.CANCELLED, Some(userName))
                          try {
                            indexingServiceLocator.byDomain.deleteBySpec(set.orgId, set.spec)
                          } catch {
                            case t: Throwable =>
                              log.warn("Error while trying to remove cancelled set from index", t)
                              DataSet.dao.updateState(set, DataSetState.ERROR, Some(userName), Some(t.getMessage))
                          }
                        case _ => send(s, error("Cannot cancel processing of a set that is not queued or processing"))
                      }
                    }
                }

              case "resetHashesForSet" =>
                withEditableSet {
                  set =>
                    {
                      set.state match {
                        case DISABLED | ENABLED | UPLOADED | ERROR | PARSING =>
                          DataSet.dao.invalidateHashes(set)
                          DataSet.dao.updateState(set, DataSetState.INCOMPLETE, Some(userName))
                        case _ => send(s, error("Cannot reset hashes of a set that is not enabled, disabled, uploaded, in error, or parsing"))
                      }
                    }
                }

              case "unlockSet" =>
                withEditableSet {
                  set =>
                    val user = HubUser.dao.findByUsername(userName)
                    DataSet.dao.findBySpecAndOrgId(spec, orgId).map { locked =>
                      locked.getLockedBy.map { lockedBy =>
                        DataSet.dao.unlock(locked, userName)
                        Email(configuration.emailTarget.systemFrom, "Your DataSet has been unlocked by another user").
                          to(lockedBy.email).
                          cc(user.map(_.email).getOrElse("")).
                          withContent(
                            s"""
                            |Hello,
                            |
                            |this email is to inform you that the DataSet with identifier "${locked.spec}" has been unlocked by ${user.map(_.fullname).getOrElse("")}
                            |
                            |Yours truthfully,
                            |
                            |The CultureBot
                          """.stripMargin).send()
                      }
                    }
                }

              case "deleteSet" =>
                withAdministrableSet {
                  set =>
                    {
                      set.state match {
                        case INCOMPLETE | DISABLED | ERROR | UPLOADED =>
                          DataSet.dao.delete(set)
                          DataSetEvent ! DataSetEvent.Removed(orgId, spec, userName)
                        case _ => send(s, error("Cannot delete set that is in use (i.e. processing, enabled, or parsing)"))
                      }
                    }
                }

              case "ping" =>
                timedoutSubscribers = timedoutSubscribers.filterNot { case (k, v) => k == clientId }

              case _ => send(s, JsObject(
                Seq(
                  "eventType" -> JsString("unknown")
                )
              ))
            }
          }
      }

    case StartPolling =>
      // since we're in a multi-node environment we can only but poll the db for updates
      pollScheduler = Akka.system.scheduler.schedule(0 seconds, 1 seconds, DataSetEventFeed.default, Update)
      deadClientCheckScheduler = Akka.system.scheduler.schedule(0 seconds, 10 seconds, DataSetEventFeed.default, CheckForDeadClients)
      log.debug("Started periodical polling of database for new DataSetEvents, polling every second")

    case StopPolling =>
      pollScheduler.cancel()
      deadClientCheckScheduler.cancel()
      log.debug("Stopped periodical polling of database for new DataSetEvents")

    case CheckForDeadClients =>
      timedoutSubscribers.foreach {
        case (clientId, timeOutCount) =>
          if (timeOutCount > 3) {
            // bye
            log.debug(s"Removing client $clientId since it's not been pinging us for a while")
            subscribers = subscribers.filterNot { case (k, v) => k == clientId }
            timedoutSubscribers = timedoutSubscribers.filterNot { case (k, v) => k == clientId }
          } else {
            timedoutSubscribers = timedoutSubscribers.updated(clientId, timeOutCount + 1)
          }
      }
      val notHere = subscribers.keys.toSet.diff(timedoutSubscribers.keys.toSet)
      timedoutSubscribers = timedoutSubscribers ++ notHere.map { clientId => clientId -> 0 }.toMap

    case Update =>
      DataSetEventLog.all.foreach {
        dsel =>
          {
            val recentEvents = dsel.findRecent.filter(r => r._id.getTime > lastSeen)
            if (!recentEvents.isEmpty) lastSeen = recentEvents.reverse.head._id.getTime

            // handle subscribers differently depending on whether they follow the list or a single set

            val listSubscribers = subscribers.filterNot(_._2.spec.isDefined)
            recentEvents.filter(e => LIST_FEED_EVENTS.contains(EventType(e.eventType))).foreach {
              event =>
                {
                  log.debug(s"Broadcasting DataSet event to ${listSubscribers.size} list subscribers: " + event.toString)
                  val msg = EventType(event.eventType) match {
                    case EventType.CREATED | EventType.UPDATED =>
                      DataSet.dao(event.orgId).findBySpecAndOrgId(event.spec, event.orgId).map {
                        set =>
                          {
                            val viewModel: ListDataSetViewModel = dataSetToListViewModel(set)
                            viewModel.toJson
                          }
                      }
                    case _ =>
                      // for the rest, just use a default mechanism
                      event.payload.map {
                        payload =>
                          JsObject(
                            Seq(
                              "payload" -> JsString(payload)
                            )
                          )
                      }
                  }
                  notifySubscribers(listSubscribers, event.orgId, event.spec, event.eventType, msg)
                }
            }

            val setSubscribers = subscribers.filter(_._2.spec.isDefined)
            val watchedSets = setSubscribers.map(_._2.spec.get).toSeq
            recentEvents.groupBy(_.spec).foreach {
              e =>
                {
                  val set = e._1
                  // only consider watched sets
                  e._2.filter(s => watchedSets.contains(s.spec)).foreach {
                    event =>
                      log.debug("Broadcasting DataSet event to all subscribers of set %s: ".format(set) + event.toString)
                      val msg = EventType(event.eventType) match {
                        case EventType.UPDATED =>
                          DataSet.dao(event.orgId).findBySpecAndOrgId(event.spec, event.orgId).map {
                            set =>
                              {
                                val viewModel: DataSetViewModel = set
                                JsObject(
                                  Seq(
                                    "eventType" -> JsString("updated"),
                                    "payload" -> viewModel.toJson
                                  )
                                )
                              }
                          }
                        case _ =>
                          // for the rest, just use a default mechanism
                          event.payload.map {
                            payload =>
                              JsObject(
                                Seq(
                                  "payload" -> JsString(payload)
                                )
                              )
                          }
                      }
                      notifySubscribers(setSubscribers.filter(_._2.spec == Some(set)), event.orgId, event.spec, event.eventType, msg)

                  }
                }
            }
          }
      }
  }

  def error(message: String) = JsObject(
    Seq(
      "eventType" -> JsString("serverError"),
      "payload" -> JsString(message)
    )
  )

  val ok = JsObject(
    Seq(
      "eventType" -> JsString("ok")
    )
  )

  def send(subscriber: Subscriber, msg: JsValue) {
    log.debug(s"Pushing message to subscriber ${subscriber.userName}: $msg")
    subscriber.channel push msg
  }

  def notifySubscribers(subscribers: Map[String, Subscriber], orgId: String, spec: String, eventType: String, msg: Option[JsObject]) {
    val default = JsObject(
      Seq(
        "orgId" -> JsString(orgId),
        "spec" -> JsString(spec),
        "eventType" -> JsString(eventType)
      )
    )

    val message = if (msg.isDefined) default ++ msg.get else default

    subscribers.filter(_._2.orgId == orgId).foreach {
      case (_, subscriber) =>
        val msg = default ++ message
        log.debug("Pushing messag to subscriber: " + msg)
        subscriber.channel push (msg)
    }
  }

  case class Subscriber(orgId: String, userName: String, configuration: String, spec: Option[String], enumerator: Enumerator[JsValue], channel: Concurrent.Channel[JsValue])

}

/**
 * This actor simply saves a message into a queue in mongo
 */
class DataSetEventLogger extends Actor {

  def receive = {

    case DataSetEvent(orgId, spec, eventType, payload, userName, systemEvent, transientEvent) =>
      implicit val configuration: OrganizationConfiguration = OrganizationConfigurationHandler.getByOrgId(orgId)
      DataSetEventLog.dao.insert(
        DataSetEventLog(
          orgId = orgId,
          spec = spec,
          eventType = eventType.name,
          payload = payload,
          userName = userName,
          systemEvent = systemEvent,
          transientEvent = transientEvent)
      )
    case _ => // do nothing

  }
}

case class DataSetEvent(orgId: String,
  spec: String,
  eventType: EventType,
  payload: Option[String] = None,
  userName: Option[String] = None,
  systemEvent: Boolean = false,
  transientEvent: Boolean = false)

case class EventType(name: String)

object EventType {
  val CREATED = EventType("created")
  val UPDATED = EventType("updated")
  val REMOVED = EventType("removed")
  val STATE_CHANGED = EventType("stateChanged")
  val ERROR = EventType("error")
  val LOCKED = EventType("locked")
  val UNLOCKED = EventType("unlocked")

  // ~~~ transient events, for progress report only
  val SOURCE_RECORD_COUNT_CHANGED = EventType("sourceRecordCountChanged")
  val PROCESSED_RECORD_COUNT_CHANGED = EventType("processedRecordCountChanged")

  // ~~~ TODO: these are temporary events used as a workaround to not having a state model reflecting these transitions
  val INVALID_RECORD_COUNT_CHANGED = EventType("invalidRecordCountChanged")

}

object DataSetEvent {

  lazy val logger = Akka.system.actorOf(Props[DataSetEventLogger])

  def !(e: DataSetEvent) {
    logger ! e
  }

  def Created(orgId: String, spec: String, userName: String) = DataSetEvent(orgId, spec, EventType.CREATED)
  def Updated(orgId: String, spec: String, userName: String) = DataSetEvent(orgId, spec, EventType.UPDATED)
  def Removed(orgId: String, spec: String, userName: String) = DataSetEvent(orgId, spec, EventType.REMOVED)

  def StateChanged(orgId: String, spec: String, state: DataSetState, userName: Option[String] = None) = DataSetEvent(orgId, spec, EventType.STATE_CHANGED, Some(state.name), userName, userName.isEmpty)
  def Error(orgId: String, spec: String, message: String, userName: Option[String] = None) = DataSetEvent(orgId, spec, EventType.ERROR, Some(message), userName, userName.isEmpty)

  def Locked(orgId: String, spec: String, userName: String) = DataSetEvent(orgId, spec, EventType.STATE_CHANGED, Some(userName), Some(userName))
  def Unlocked(orgId: String, spec: String, userName: String) = DataSetEvent(orgId, spec, EventType.UNLOCKED, None, Some(userName))

  def SourceRecordCountChanged(orgId: String, spec: String, count: Long) = DataSetEvent(orgId, spec, EventType.SOURCE_RECORD_COUNT_CHANGED, Some(count.toString), None, true, true)
  def ProcessedRecordCountChanged(orgId: String, spec: String, count: Long) = DataSetEvent(orgId, spec, EventType.PROCESSED_RECORD_COUNT_CHANGED, Some(count.toString), None, true, true)

  // ~~~ TODO: these are temporary events used as a workaround to not having a state model reflecting these transitions
  def InvalidRecordCountChanged(orgId: String, spec: String, prefix: String, count: Long) = DataSetEvent(orgId, spec, EventType.INVALID_RECORD_COUNT_CHANGED, Some(count.toString), None, true)

}