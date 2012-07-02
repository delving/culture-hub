package controllers.organization

import akka.actor._
import akka.util.duration._
import akka.util.Timeout
import akka.pattern.ask
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.Play.current

/**
 * TODO add methods to DataSetListFeed object that result in sending the appropriate messages to all subscribers
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class DataSetListFeed extends Actor {

  import DataSetListFeed._

  implicit val timeout = Timeout(1 second)

  var members = Map.empty[String, PushEnumerator[JsValue]]

  def receive = {

    case Subscribe(clientId) => {
      // Create an Enumerator to write to this socket
      val channel = Enumerator.imperative[JsValue]()
      if (members.contains(clientId)) {
        sender ! CannotConnect("This clientId is already used")
      } else {
        members = members + (clientId -> channel)

        // TODO send initial list of sets

        sender ! Connected(channel)
      }
    }

  }

  def notifyAllSubscribers(orgId: String, spec: String, eventType: String, msg: JsObject) {
    val default = JsObject(
      Seq(
        "orgId" -> JsString(orgId),
        "spec" -> JsString(spec),
        "eventType" -> JsString(eventType)
      )
    )

    members.foreach {
      case (_, channel) => channel.push(default ++ msg)
    }
  }

}

object DataSetListFeed {

  lazy val default = {
    Akka.system.actorOf(Props[DataSetListFeed])
  }

  def subscribe(clientId: String): Promise[(Iteratee[JsValue, _], Enumerator[JsValue])] = {

    implicit val timeout = Timeout(1 second)

    (default ? Subscribe(clientId)).asPromise.map {

      case Connected(enumerator) =>

        val iteratee = Iteratee.foreach[JsValue] {
          event => // don't do a single thing here
        }.mapDone {
          _ =>
            default ! Unsubscribe(clientId)
        }

        (iteratee, enumerator)

      case CannotConnect(error) =>

        // Connection error

        // A finished Iteratee sending EOF
        val iteratee = Done[JsValue, Unit]((), Input.EOF)

        // Send an error and close the socket
        val enumerator = Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))

        (iteratee, enumerator)

    }
  }

  case class Subscribe(clientId: String)
  case class Unsubscribe(clientId: String)

  case class Connected(enumerator: Enumerator[JsValue])
  case class CannotConnect(msg: String)

  case class LoadList(list: Seq[DataSetViewModel])

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
                              lockedBy: String) extends Writes[DataSetViewModel] {

    def writes(o: DataSetViewModel): JsValue = JsObject(
      Seq(
        "spec" -> JsString(spec),
        "name" -> JsString(name),
        "nodeId" -> JsString(nodeId),
        "nodeName" -> JsString(nodeName),
        "totalRecords" -> JsNumber(totalRecords),
        "state" -> JsString(state),
        "lockState" -> JsString("lockState"),
        "lockedBy" -> JsString("lockedBy")
      )
    )
  }

}