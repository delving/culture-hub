package core.services

import core.node.{NodeSubscriptionService, Node}
import core.HubModule
import models.DomainConfiguration
import collection.mutable.ArrayBuffer

/**
 * For the moment, only aware of Virtual Nodes, but should become a hybrid bridge between XMPP and Virtual Nodes
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class HubNodeSubscriptionService extends NodeSubscriptionService {

  private val broadcastingNodeSubscriptionService: NodeSubscriptionService = HubModule.inject[NodeSubscriptionService](name = None)

  // TODO this needs to become persistent
  private val buddies = new ArrayBuffer[Node]

  def generateSubscriptionRequest(to: Node, from: Node)(implicit configuration: DomainConfiguration) {
    if (to.nodeId == configuration.node.nodeId) {
      broadcastingNodeSubscriptionService.processSubscriptionRequest(to, from)
    }
  }

  def generateSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: DomainConfiguration) {
    if (from.nodeId == configuration.node.nodeId) {
      broadcastingNodeSubscriptionService.processSubscriptionResponse(to, from, accepted)
    }
  }


  def processSubscriptionRequest(to: Node, from: Node)(implicit configuration: DomainConfiguration) {
    if (to.nodeId == configuration.node.nodeId && !buddies.exists(_.nodeId == from.nodeId)) {
      // for the time being we accept blindly everyone since requests can only come from our own Virtual Nodes
      buddies += from
      generateSubscriptionResponse(from, to, true)
    }
  }

  def processSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: DomainConfiguration) {
    if (to.nodeId == configuration.node.nodeId && accepted) {
      buddies += from
    }

  }

  def listActiveSubscriptions(node: Node)(implicit configuration: DomainConfiguration): Seq[Node] = {
    if (node.nodeId == configuration.node.nodeId) buddies.toSeq else Seq.empty
  }

}
