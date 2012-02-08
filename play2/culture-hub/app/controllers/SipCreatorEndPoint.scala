package controllers

import exceptions.AccessKeyException
import play.api.mvc._
import models._
import eu.delving.metadata.MetadataModel
import core.mapping.MappingService
import org.apache.commons.io.FileCleaningTracker
import play.api.Logger

/**
 * This Controller is responsible for all the interaction with the SIP-Creator.
 * Access control is done using OAuth2
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object SipCreatorEndPoint extends ApplicationController {

  private val UNAUTHORIZED_UPDATE = "You do not have the necessary rights to modify this data set"
  private val metadataModel: MetadataModel = MappingService.metadataModel
  private val fileCleaningTracker = new FileCleaningTracker

  val DOT_PLACEHOLDER = "--"

  // HASH__type[_prefix].extension
  private val FileName = """([^_]*)__([^._]*)_?([^.]*).(.*)""".r

  private var connectedUserObject: Option[User] = None

  private var connectedOrg: Option[Organization] = None


  def AuthenticatedAction[A](accessToken: Option[String])(action: Action[A]): Action[A] = {
    Action(action.parser) {
      implicit request => {
        if (accessToken.isEmpty) {
          Unauthorized("No access token provided")
        } else if (!OAuth2TokenEndpoint.isValidToken(accessToken.get)) {
          Unauthorized("Access Key %s not accepted".format(accessToken))
        } else {
          connectedUserObject = OAuth2TokenEndpoint.getUserByToken(accessToken.get)
          val updatedSession = session + (Authentication.USERNAME -> connectedUserObject.get.userName)
          val r: PlainResult = action(request).asInstanceOf[PlainResult]
          r.withSession(updatedSession)
        }
      }
    }
  }

  def OrganizationAction[A](orgId: String, accessToken: Option[String])(action: Action[A]): Action[A] = AuthenticatedAction(accessToken) {
    Action(action.parser) {
      implicit request =>
        if (orgId == null || orgId.isEmpty) {
          BadRequest("No orgId provided")
        } else {
          connectedOrg = Organization.findByOrgId(orgId)
          if (connectedOrg == None) {
            NotFound("Unknown organization " + orgId)
          } else {
            action(request)
          }
        }
    }
  }

  def getConnectedUser: User = connectedUserObject.getOrElse({
    Logger("CultureHub").warn("Attemtping to connect with an invalid access token")
    throw new AccessKeyException("No access token provided")
  })

  def getConnectedUserId = getConnectedUser._id
  def connectedUser = getConnectedUser.userName


  def listAll(accessToken: Option[String]) = AuthenticatedAction(accessToken) {
    Action {
      implicit request =>
        val dataSets = DataSet.findAllForUser(connectedUserObject.get.userName, GrantType.MODIFY)

            val dataSetsXml = <data-set-list>
              {dataSets.map {
              ds =>
                val creator = ds.getCreator
                val lockedBy = ds.getLockedBy
                <data-set>
                  <spec>{ds.spec}</spec>
                  <name>{ds.details.name}</name>
                  <orgId>{ds.orgId}</orgId>
                  <createdBy>
                    <username>{creator.userName}</username>
                    <fullname>{creator.fullname}</fullname>
                    <email>{creator.email}</email>
                  </createdBy>{if (lockedBy != None) {
                  <lockedBy>
                    <username>{lockedBy.get.userName}</username>
                    <fullname>{lockedBy.get.fullname}</fullname>
                    <email>{lockedBy.get.email}</email>
                  </lockedBy>}}
                  <state>{ds.state.toString}</state>
                  <recordCount>{ds.details.total_records}</recordCount>
                </data-set>
              }
            }
            </data-set-list>

            Ok(dataSetsXml)
    }
  }

  def unlock(orgId: String, spec: String, accessToken: Option[String]): Action[AnyContent] = OrganizationAction(orgId, accessToken) {
    Action {
      implicit request =>
        val dataSet = DataSet.findBySpecAndOrgId(spec, orgId)
          if(dataSet.isEmpty) {
          val msg = "Unknown spec %s".format(spec)
          NotFound(msg)
        } else {
            if(dataSet.get.lockedBy == None) {
              Ok
            } else if(dataSet.get.lockedBy.get == getConnectedUserId) {
              val updated = dataSet.get.copy(lockedBy = None)
              DataSet.save(updated)
              Ok
            } else {
              Error("You cannot unlock a DataSet locked by someone else")
            }
        }
    }
  }

  /**
   * Takes a request of filenames and replies with the ones it is missing:
   *
   * 15E64004081B71EE5CA8D55EF735DE44__hints.txt
   * 19EE613335AFBFFAD3F8BA271FBC4E96__mapping_icn.xml
   * 45109F902FCE191BBBFC176287B9B2A4__source.xml.gz
   * 19EE613335AFBFFAD3F8BA271FBC4E96__valid_icn.bit
   */
  def acceptFileList(orgId: String, spec: String, accessToken: Option[String]): Action[AnyContent] = OrganizationAction(orgId, accessToken) {
    Action {
      implicit request =>

        val dataSet = DataSet.findBySpecAndOrgId(spec, orgId)
          if(dataSet.isEmpty) {
          val msg = "DataSet with spec %s not found".format(spec)
          NotFound(msg)
        } else {
            val fileList: String = request.body.asText.getOrElse("")

            Logger("CultureHub").debug("Receiving file upload request, possible files to receive are: \n" + fileList)

            val lines = fileList split('\n')

            def fileRequired(fileName: String): Option[String] = {
              val Array(hash, name) = fileName split("__")
              val maybeHash = dataSet.get.hashes.get(name.replaceAll("\\.", DOT_PLACEHOLDER))
              maybeHash match {
                case Some(storedHash) if hash != storedHash => Some(fileName)
                case Some(storedHash) if hash == storedHash => None
                case None => Some(fileName)
              }
          }
          val requiredFiles = (lines flatMap fileRequired).mkString("\n")
          Ok(requiredFiles)
        }
    }
  }






}
