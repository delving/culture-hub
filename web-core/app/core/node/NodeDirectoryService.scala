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

}
