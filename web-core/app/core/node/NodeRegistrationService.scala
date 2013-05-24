package core.node

/**
 * This service allows to register a Node against a centralized lookup service for nodes across a network.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait NodeRegistrationService {

  /**
   * Registers a new node. The organization doing the request will automatically become the owner.
   *
   * @param node the node to register
   * @param userName the userName of who registered the node
   */
  def registerNode(node: Node, userName: String)

  /**
   * Updates a node information
   *
   * @param node the node to update
   */
  def updateNode(node: Node)

  /**
   * Removes a node
   *
   * @param node the node to update
   */
  def removeNode(node: Node)

  /**
   * List all members of this node
   *
   * @param node the node to list users of
   * @return a list of userNames
   */
  def listMembers(node: Node): Seq[String]

  /**
   * Adds a member to a node
   *
   * @param node the node to update
   * @param userName the member to add
   */
  def addMember(node: Node, userName: String)

  /**
   * Removes a member from a node
   *
   * @param node the node to update
   * @param userName the member to remove
   */
  def removeMember(node: Node, userName: String)

}