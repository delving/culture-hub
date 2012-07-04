package controllers.organization

import akka.actor._
import akka.util.duration._
import akka.util.Timeout
import akka.pattern.ask
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.Play.current
import models.{DataSetEventLog, DataSetState, DataSet}
import play.api.Logger


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSetEventFeed {

  val log = Logger(getClass)

  implicit def dataSetToViewModel(ds: DataSet): DataSetViewModel = DataSetViewModel(
    spec = ds.spec,
    orgId = ds.orgId,
    name = ds.getName,
    nodeId = "", // TODO once we have nodes...
    nodeName = ds.getProvider,
    totalRecords = ds.getTotalRecords,
    state = ds.state.name,
    lockState = if(ds.lockedBy.isDefined) "locked" else "unlocked",
    lockedBy = if(ds.lockedBy.isDefined) ds.lockedBy.get else ""
  )

  implicit def dataSetListToViewModelList(dsl: Seq[DataSet]): Seq[DataSetViewModel] = dsl.map(dataSetToViewModel(_))


  lazy val default = {

    val feed = Akka.system.actorOf(Props[DataSetEventFeed])

    // since we're in a multi-node environment we can only but poll the db for updates
    Akka.system.scheduler.schedule(
      2 seconds,
      2 seconds,
      feed,
      Update
    )

    feed
  }

  def subscribe(orgId: String, clientId: String): Promise[(Iteratee[JsValue, _], Enumerator[JsValue])] = {

    log.debug("Client %s of org %s requesting subscribtion to DataSet feed".format(clientId, orgId))

    implicit val timeout = Timeout(1 second)

    (default ? Subscribe(orgId, clientId)).asPromise.map {

      case Connected(enumerator) =>

        log.debug("Client %s connected to feed".format(clientId))

        val iteratee = Iteratee.foreach[JsValue] {
          event => default ! ClientMessage(event)
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

  case class Subscribe(orgId: String, clientId: String)
  case class Unsubscribe(clientId: String)

  case class Connected(enumerator: PushEnumerator[JsValue])
  case class CannotConnect(msg: String)

  case object Update

  case class ClientMessage(message: JsValue)

  case class AddSet(set: DataSetViewModel)
  case class UpdateSet(spec: String, nodeId: String, nodeName: String, name: String)
  case class RemoveSet(spec: String)

  case class UpdateTotalRecords(spec: String, totalRecords: Long)
  case class UpdateState(spec: String, state: String)
  case class UpdateLockState(spec: String, lockState: String)

  case class DataSetViewModel(spec: String,
                              orgId: String,
                              name: String,
                              nodeId: String,
                              nodeName: String,
                              totalRecords: Long,
                              state: String,
                              lockState: String,
                              lockedBy: String) {

    def toJson: JsObject = JsObject(
      Seq(
        "spec" -> JsString(spec),
        "orgId" -> JsString(orgId),
        "name" -> JsString(name),
        "nodeId" -> JsString(nodeId),
        "nodeName" -> JsString(nodeName),
        "totalRecords" -> JsNumber(totalRecords),
        "state" -> JsString(state),
        "lockState" -> JsString(lockState),
        "lockedBy" -> JsString(lockedBy)
      )
    )
  }

}

class DataSetEventFeed extends Actor {

  import DataSetEventFeed._

  implicit val timeout = Timeout(1 second)

  var lastSeen: Long = System.currentTimeMillis()

  var subscribers = Map.empty[String, Subscriber]

  def receive = {

    case Subscribe(orgId, clientId) => {
      // Create an Enumerator to write to this socket

      val channel = Enumerator.imperative[JsValue]()

      if (subscribers.contains(clientId)) {
        log.warn("Duplicate clientId connection attempt from " + clientId)
        sender ! CannotConnect("This clientId is already used")
      } else {
        subscribers = subscribers + (clientId -> Subscriber(orgId, channel))
        sender ! Connected(channel)
      }
    }

    case Unsubscribe(clientId) =>
      subscribers = subscribers - clientId

    case ClientMessage(message) =>
      val clientId: String = (message \ "clientId").asOpt[Int].getOrElse(0).toString // the Play JSON API is really odd...
      val eventType: String = (message \ "eventType").asOpt[String].getOrElse("")

      subscribers.find(_._1 == clientId).map {
        subscriber =>
          eventType match {
            case "sendList" =>
              log.debug("About to send complete list of sets to client " + clientId)
              val sets: Seq[DataSetViewModel] = DataSet.findAllByOrgId(subscriber._2.orgId).toSeq
              val jsonList = sets.map(_.toJson).toSeq
              val list = JsArray(jsonList)
              val msg = JsObject(
                Seq(
                  "eventType" -> JsString("loadList"),
                  "payload" -> list
                )
              )
              subscriber._2.channel.push(msg)
            case _ => // nothing
          }
      }

    case Update =>
      val recentEvents = DataSetEventLog.findRecent.filter(r => r._id.getTime > lastSeen)
      if(!recentEvents.isEmpty) lastSeen = recentEvents.reverse.head._id.getTime

      recentEvents.foreach {
        event => {
          log.debug("Broadcasting DataSet event: " + event.toString)
          val msg = EventType(event.eventType) match {
            case EventType.CREATED | EventType.UPDATED =>
              DataSet.findBySpecAndOrgId(event.spec, event.orgId).map {
                set => {
                  val viewModel: DataSetViewModel = set
                  viewModel.toJson
                }
            }
            case _ =>
              // for the rest, just use a default mechanism
              event.payload.map {
                payload => JsObject(
                  Seq(
                    "payload" -> JsString(payload)
                  )
                )
              }
          }
          notifySubscribers(event.orgId, event.spec, event.eventType, msg)
        }
      }
  }

  def notifySubscribers(orgId: String, spec: String, eventType: String, msg: Option[JsObject]) {
    val default = JsObject(
      Seq(
        "orgId" -> JsString(orgId),
        "spec" -> JsString(spec),
        "eventType" -> JsString(eventType)
      )
    )

    val message = if(msg.isDefined) default ++ msg.get else default

    subscribers.filter(_._2.orgId == orgId).foreach {
      case (_, subscriber) =>
        val msg = default ++ message
        log.debug("Pushing messag to subscriber: " + msg)
        subscriber.channel.push(msg)
    }
  }

  case class Subscriber(orgId: String, channel: PushEnumerator[JsValue])

}

/**
 * This actor simply saves a message into a queue in mongo
 */
class DataSetEventLogger extends Actor {

  def receive = {

    case DataSetEvent(orgId, spec, eventType, payload, userName, systemEvent) =>
      DataSetEventLog.insert(DataSetEventLog(orgId = orgId, spec = spec, eventType = eventType.name, payload = payload, userName = userName, systemEvent = systemEvent))
    case _ => // do nothing

  }
}

case class DataSetEvent(orgId: String, spec: String, eventType: EventType, payload: Option[String] = None, userName: Option[String] = None, systemEvent: Boolean = false)

case class EventType(name: String)
object EventType {
  val CREATED = EventType("created")
  val UPDATED = EventType("updated")
  val REMOVED = EventType("removed")
  val SOURCE_UPLOADED = EventType("sourceUploaded")
  val SOURCE_RECORD_COUNT_CHANGED = EventType("sourceRecordCountChanged")
  val STATE_CHANGED = EventType("stateChanged")
  val LOCKED = EventType("locked")
  val UNLOCKED = EventType("unlocked")
}

object DataSetEvent {

  lazy val logger = Akka.system.actorOf(Props[DataSetEventLogger])

  def Created(orgId: String, spec: String, userName: String) = DataSetEvent(orgId, spec, EventType.CREATED)
  def Updated(orgId: String, spec: String, userName: String) = DataSetEvent(orgId, spec, EventType.UPDATED)
  def Removed(orgId: String, spec: String, userName: String) = DataSetEvent(orgId, spec, EventType.REMOVED)

  def SourceUploaded(orgId: String, spec: String, userName: String) = DataSetEvent(orgId, spec, EventType.SOURCE_UPLOADED, None, Some(userName))
  def SourceRecordCountChanged(orgId: String, spec: String, count: Long) = DataSetEvent(orgId, spec, EventType.SOURCE_RECORD_COUNT_CHANGED, Some(count.toString), None, true)
  def StateChanged(orgId: String, spec: String, state: DataSetState, userName: Option[String]) = DataSetEvent(orgId, spec, EventType.STATE_CHANGED, Some(state.name), userName, userName.isEmpty)
  def Locked(orgId: String, spec: String, userName: String) = DataSetEvent(orgId, spec, EventType.STATE_CHANGED, Some(userName), Some(userName))
  def Unlocked(orgId: String, spec: String, userName: String) = DataSetEvent(orgId, spec, EventType.UNLOCKED, None, Some(userName))

}