package controllers.organization

import play.api.mvc._
import controllers.OrganizationController
import eu.delving.LaunchFile
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

  private val format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")

  def index = OrganizationMember {
    Action {
      implicit request => Ok(Template('orgId -> configuration.orgId))
    }
  }

  def jnlp(user: String) = Root {

    Action {
      implicit request =>

        val host = request.domain + (if (Play.isDev) ":9000" else ":80") // we need the port for the sip-creator
        val home = "http://" + host + "/" + user
        val codebase = "http://" + host + "/assets/sip-creator/"

        val jnlp = LaunchFile.createJNLP(home, codebase, user)

        val lastModified = {
          val url = this.getClass.getResource("/eu/delving/LaunchFile.class")
          new Date(url.openConnection().getLastModified)
        }

        Ok(jnlp).
          as("application/x-java-jnlp-file").
          withHeaders(
            (CACHE_CONTROL, "no-cache"),
            (LAST_MODIFIED, format.format(lastModified))
          )
    }
  }

}