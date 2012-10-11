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
    broadcastingNodeSubscriptionService.generateSubscriptionRequest(to, from)
  }

  def generateSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: DomainConfiguration) {
    broadcastingNodeSubscriptionService.generateSubscriptionResponse(to, from, accepted)
  }


  def processSubscriptionRequest(to: Node, from: Node)(implicit configuration: DomainConfiguration) {
    // for the time being we accept blindly everyone since requests can only come from our own Virtual Nodes
    generateSubscriptionResponse(from, to, true)
  }

  def processSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: DomainConfiguration) {
    if (to == configuration.node && accepted) {
      buddies += from
    }

  }

  def listActiveSubscriptions(node: Node)(implicit configuration: DomainConfiguration): Seq[Node] = {
    if (node == configuration.node) buddies.toSeq else Seq.empty
  }

}
