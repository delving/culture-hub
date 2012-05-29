package controllers.statistics

import controllers.OrganizationController
import play.api.mvc.Action

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSetStatistics extends OrganizationController {

  def statistics(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        Ok(Template("statistics.html"))
    }
  }

}
