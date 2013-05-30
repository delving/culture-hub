package services

import core.HubId
import core.rendering.FullViewService

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class EADFullViewService extends FullViewService {
  def prefix: String = "ead"

  def getView(hubId: HubId): (String, Seq[(Symbol, AnyRef)]) = ("/ead/EAD/view.html", Seq('id -> Some(hubId.toString)))
}
