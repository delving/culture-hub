package controllers.mediator.organization

import controllers.OrganizationController
import play.api.mvc._
import plugins.MediatorPlugin
import java.io.File
import extensions.Email
import models.HubUser
import util.Quotes

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object Mediator extends OrganizationController {

  def collection(collection: String) = OrganizationAdmin {

    Action { implicit request =>

      val collectionSourceDir = new File(MediatorPlugin.pluginConfiguration.sourceDirectory, collection)
      if (!collectionSourceDir.exists()) collectionSourceDir.mkdir()

      val files = collectionSourceDir.listFiles

      Ok
    }
  }

  def newFileFault(orgId: String, set: String, fileName: String, userName: String, error: Option[String]) = Action { implicit request =>
    log.debug(s"[$userName@$orgId] Received file handling response from media server for $set/$fileName, ${if (error.isDefined) "with error: " + error.get}")

    error.map { message =>

      val content: String = s"""
                  |Master,
                  |
                  |there was a problem while processing the file '$fileName' that you have uploaded to the Mediator-managed FTP of organization $orgId.
                  |
                  |The error is:
                  |
                  |$message
                  |
                  |
                  |Yours truly,
                  |
                  |The Mediator
                  |
                  |----
                  |${Quotes.randomQuote()}
                """.stripMargin

      HubUser.dao.findByUsername(userName).map { user =>
        Email(configuration.emailTarget.systemFrom, s"[Mediator] Error while processing file '$fileName'").
          to(user.email).
          withContent(content).
          send()
      }.getOrElse {
        log.error(s"Could not find user $userName, the content of the mail would have been:\n\n" + content)
      }
    }

    Ok
  }

}
