package services

import core.node.{Node, NodeSubscriptionService}
import models.{VirtualNode, DomainConfiguration}
import org.scala_tools.subcut.inject.{BindingModule, Injectable}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class VirtualNodeSubscriptionService(implicit val bindingModule: BindingModule) extends NodeSubscriptionService with Injectable {

  private val broadcastingHubNodeService = inject [NodeSubscriptionService ]

  def generateSubscriptionRequest(to: Node, from: Node)(implicit configuration: DomainConfiguration) {
    broadcastingHubNodeService.processSubscriptionRequest(to, from)
  }

  def generateSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: DomainConfiguration) {
    broadcastingHubNodeService.processSubscriptionResponse(to, from, accepted)
  }

  def processSubscriptionRequest(to: Node, from: Node)(implicit configuration: DomainConfiguration) {
    // for the moment, accept blindly
    VirtualNode.dao.findOne(to).foreach { receiver =>
      VirtualNode.addContact(receiver, from)
      generateSubscriptionResponse(from, to, true)
    }
  }

  def processSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: DomainConfiguration) {
    if (accepted) {
      VirtualNode.dao.findOne(to).foreach { receiver =>
        VirtualNode.addContact(receiver, from)
      }
    }
  }

  def listActiveSubscriptions(node: Node)(implicit configuration: DomainConfiguration): Seq[Node] = {
    VirtualNode.dao.findOne(node).map { node =>
      node.contacts.flatMap { contact =>
        VirtualNode.dao.findOne(contact)
      }
    }.getOrElse(Seq.empty)
  }
}
