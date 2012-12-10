package controllers.organization

import play.api.mvc._
import controllers.OrganizationController
import eu.delving.LaunchFile
import java.util.Date

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object SipCreator extends OrganizationController {

  def index(orgId: String) = OrganizationMember {
    Action {
      implicit request => Ok(Template('orgId -> orgId))
    }
  }

  def jnlp(user: String) = Root {

    Action {
      implicit request =>

        val host = request.domain + ":9000"
        val home = "http://" + host + "/" + user + "/"
        val codebase = "http://" + host + "/assets/sip-creator/"

        val jnlp = LaunchFile.createJNLP(home, codebase, user)

        val lastModified = {
          val url = SipCreator.getClass.getResource("/eu/delving/LaunchFile.class")
          new Date(url.openConnection().getLastModified)
        }

        Ok(jnlp).
          as("application/x-java-jnlp-file").
          withHeaders(
          (CACHE_CONTROL, "no-cache"),
          (LAST_MODIFIED, lastModified.getTime.toString)
        )
    }
  }

}
