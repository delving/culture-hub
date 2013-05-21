package core.node

/**
 * A directory of nodes
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait NodeDirectoryService {

  /**
   * Lists all known entries of the directory
   * @return a sequence of [[core.node.Node]]
   */
  def listEntries: Seq[Node]

  /**
   * Finds one node by ID
   * @param nodeId the unique ID of the node
   * @return an optional [[core.node.Node]]
   */
  def findOneById(nodeId: String): Option[Node]

}