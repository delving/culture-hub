package core.services

import core.CultureHubPlugin
import core.node.{Node, NodeSubscriptionService}
import models.OrganizationConfiguration

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class AggregatingNodeSubscriptionService extends NodeSubscriptionService {

  private def nodeConnectionServices(implicit configuration: OrganizationConfiguration) = {
    CultureHubPlugin.getServices(classOf[NodeSubscriptionService]).
      filterNot(s => s.getClass == classOf[AggregatingNodeSubscriptionService])
  }

  def listActiveSubscriptions(node: Node)(implicit configuration: OrganizationConfiguration): Seq[Node] = {
    nodeConnectionServices.flatMap { s => s.listActiveSubscriptions(node) }
  }

  def generateSubscriptionRequest(to: Node, from: Node)(implicit configuration: OrganizationConfiguration) {
    nodeConnectionServices.foreach { s => s.generateSubscriptionRequest(to, from) }
  }

  def processSubscriptionRequest(to: Node, from: Node)(implicit configuration: OrganizationConfiguration) {
    nodeConnectionServices.foreach { s => s.processSubscriptionRequest(to, from) }
  }

  def generateSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: OrganizationConfiguration) {
    nodeConnectionServices.foreach { s => s.generateSubscriptionResponse(to, from, accepted) }
  }

  def processSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: OrganizationConfiguration) {
    nodeConnectionServices.foreach { s => s.processSubscriptionResponse(to, from, accepted) }
  }
}

