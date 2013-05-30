package core.rendering

import core.HubId

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait FullViewService {

  /**
   * The prefix of the schema being handled by this services
   */
  def prefix: String

  /**
   * Gives back the necessary information for display the full view
   * @param hubId the hubId of the record to display
   * @return A tuple containing the name of the template to use for rendering and additional rendering parameters
   */
  def getView(hubId: HubId): (String, Seq[(Symbol, AnyRef)])

}
