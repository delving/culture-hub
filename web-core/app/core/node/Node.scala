package core.node

/**
 * A Node
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait Node {

  /**
   * The identifier of the node
   */
  def nodeId: String

  /**
   * The display name of the node
   */
  def name: String

  /**
   * The organization identifier of this node.
   * This is not (necessarily) the orgId of the hub!
   */
  def orgId: String

  /**
   * Is this node's primary location the current hub, or is it a remote / connected one?
   */
  def isLocal: Boolean

  override def equals(that: Any): Boolean = that.isInstanceOf[Node] && that.asInstanceOf[Node].nodeId == nodeId

  override def hashCode(): Int = nodeId.hashCode
}