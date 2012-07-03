package controllers.organization

import akka.actor._
import akka.util.duration._
import akka.util.Timeout
import akka.pattern.ask
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.Play.current
import models.DataSet
import play.api.Logger


/**
 * TODO add methods to DataSetListFeed object that result in sending the appropriate messages to all subscribers
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSetListFeed {

  val log = Logger(getClass)

  implicit def dataSetToViewModel(ds: DataSet): DataSetViewModel = DataSetViewModel(
    spec = ds.spec,
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

    val feed = Akka.system.actorOf(Props[DataSetListFeed])

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
                              name: String,
                              nodeId: String,
                              nodeName: String,
                              totalRecords: Long,
                              state: String,
                              lockState: String,
                              lockedBy: String) {

    def toJson: JsValue = JsObject(
      Seq(
        "spec" -> JsString(spec),
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

class DataSetListFeed extends Actor {

  import DataSetListFeed._

  implicit val timeout = Timeout(1 second)

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
                  "list" -> list
                )
              )
              subscriber._2.channel.push(msg)
            case _ => // nothing
          }
      }

    case Update =>
//      notifyAllSubscribers("foo", "bar", "bla", JsObject(Seq("Foo" -> JsString("bar"))))


  }

  def notifyAllSubscribers(orgId: String, spec: String, eventType: String, msg: JsObject) {
    val default = JsObject(
      Seq(
        "orgId" -> JsString(orgId),
        "spec" -> JsString(spec),
        "eventType" -> JsString(eventType)
      )
    )

    subscribers.foreach {
      case (_, subscriber) => subscriber.channel.push(default ++ msg)
    }
  }

  case class Subscriber(orgId: String, channel: PushEnumerator[JsValue])

}