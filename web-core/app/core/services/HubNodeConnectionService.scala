package core.services

import core.node.{NodeConnectionService, Node}
import core.{HubServices, HubModule}
import models.DomainConfiguration
import collection.mutable.ArrayBuffer

/**
 * For the moment, only aware of Virtual Nodes, but should become a hybrid bridge between XMPP and Virtual Nodes
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class HubNodeConnectionService extends NodeConnectionService {

  private val nodeConnectionService: NodeConnectionService = HubModule.inject[NodeConnectionService](name = None)

  // TODO this needs to become persistent
  private val buddies = new ArrayBuffer[Node]

  def generateSubscriptionRequest(to: Node, from: Node)(implicit configuration: DomainConfiguration) {
    nodeConnectionService.generateSubscriptionRequest(to, from)
  }

  def generateSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: DomainConfiguration) {
    nodeConnectionService.generateSubscriptionResponse(to, from, accepted)
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
