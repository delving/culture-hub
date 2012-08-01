package controllers.search

import play.api.mvc._
import controllers.DelvingController
import scala.collection.JavaConverters._
import models.DataSet

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object AdvancedSearch extends DelvingController {

  def advancedSearch = Root {
    Action {
      implicit request =>
        val providers = DataSet.dao.findAllByOrgId(configuration.orgId).map(_.getDataProvider).toList.distinct
        Ok(Template('providers -> providers.asJava))
    }
  }

}
