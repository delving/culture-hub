package core.services

import core.CultureHubPlugin
import core.node.{Node, NodeSubscriptionService}
import models.DomainConfiguration

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class BroadcastingNodeSubscriptionService extends NodeSubscriptionService {

  private def nodeConnectionServices(implicit configuration: DomainConfiguration) = {
    CultureHubPlugin.getServices(classOf[NodeSubscriptionService]).
      filterNot(s => s.getClass == classOf[BroadcastingNodeSubscriptionService])
  }

  def listActiveSubscriptions(node: Node)(implicit configuration: DomainConfiguration): Seq[Node] = {
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

