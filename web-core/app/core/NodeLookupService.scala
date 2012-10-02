package core

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait NodeLookupService {

  def findById(orgId: String, nodeId: String)

}
