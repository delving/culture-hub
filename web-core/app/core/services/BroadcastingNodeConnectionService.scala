package core.services

import _root_.core.CultureHubPlugin
import _root_.core.node.{Node, NodeConnectionService}
import models.DomainConfiguration

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class BroadcastingNodeConnectionService extends NodeConnectionService {

  private def nodeConnectionServices(implicit configuration: DomainConfiguration) = {
    CultureHubPlugin.getServices(classOf[NodeConnectionService]).filterNot(_ == this)
  }

  def listActiveSubscriptions(node: Node)(implicit configuration: DomainConfiguration): scala.Seq[Node] = {
    nodeConnectionServices.flatMap { s => s.listActiveSubscriptions(node) }
  }

  def generateSubscriptionRequest(to: Node, from: Node)(implicit configuration: DomainConfiguration) {
    nodeConnectionServices.foreach { s => s.generateSubscriptionRequest(to, from) }
  }

  def processSubscriptionRequest(to: Node, from: Node)(implicit configuration: DomainConfiguration) {
    nodeConnectionServices.foreach { s => s.processSubscriptionRequest(to, from) }
  }

  def generateSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: DomainConfiguration) {
    nodeConnectionServices.foreach { s => s.generateSubscriptionResponse(to, from, accepted) }
  }

  def processSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: DomainConfiguration) {
    nodeConnectionServices.foreach { s => s.processSubscriptionResponse(to, from, accepted) }
  }
}

