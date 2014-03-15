package controllers.organization

import play.api.mvc._
import controllers.OrganizationController
import java.util.Date
import java.text.SimpleDateFormat
import play.api.Play
import play.api.Play.current
import com.escalatesoft.subcut.inject.BindingModule

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class SipCreator(implicit val bindingModule: BindingModule) extends OrganizationController {

  def index = OrganizationMember {
    MultitenantAction {
      implicit request => Ok(Template('orgId -> configuration.orgId))
    }
  }

}