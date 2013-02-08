package core.node

import models.OrganizationConfiguration

/**
 * The NodeConnectionService makes it possible for Nodes to connect to each-other following a basic request / response
 * mechanism, inspired by the XMPP protocol (RFC 6121).
 *
 * @see http://xmpp.org/rfcs/rfc6121.html#sub
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait NodeSubscriptionService {

  /**
   * Requests to be connected with another node
   * @param to the target node to request connection with
   * @param from this node
   */
  def generateSubscriptionRequest(to: Node, from: Node)(implicit configuration: OrganizationConfiguration)

  /**
   * Replies to a connection request
   * @param to the node that originally requested the connection
   * @param from the node replying to the connection request
   * @param accepted whether the connection request was accepted or rejected
   */
  def generateSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: OrganizationConfiguration)

  /**
   * Handles a connection request
   * @param to the target node of the request
   * @param from the node the request came from
   */
  def processSubscriptionRequest(to: Node, from: Node)(implicit configuration: OrganizationConfiguration)

  /**
   * Handles a connection request response
   * @param to the node that originally requested the connection
   * @param from the node replying to the connection request
   * @param accepted whether the connection request was accepted or rejected
   */
  def processSubscriptionResponse(to: Node, from: Node, accepted: Boolean)(implicit configuration: OrganizationConfiguration)

  /**
   * Lists the active connections of a Node, i.e. lists nodes that the node is connected with and that are reachable
   * @param node the node for which to list the active connections
   * @return a sequence of [[core.node.Node]]
   */
  def listActiveSubscriptions(node: Node)(implicit configuration: OrganizationConfiguration): Seq[Node]

}