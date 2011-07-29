package controllers.user

import play.templates.Html
import play.mvc.Before
import controllers.{UserAuthentication, Secure, DelvingController}
import play.mvc.results.Result
import models.UserGroup
import extensions.{PlayParameterNameReader, ObjectIdSerializer, LiftJson}
import net.liftweb.json.{FullTypeHints, Serialization, DefaultFormats}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends DelvingController with UserAuthentication with Secure with LiftJson {

  import views.User.Admin._

  @Before def checkUser(): Result = {
    if (connectedUser == null || connectedUser != params.get("user")) {
      return Forbidden("You do not have access here")
    }
    Continue
  }

  def index: Html = {
    html.index()
  }

  def groupList: Html = {
    val userGroups = UserGroup.findByUser(getUserId(connectedUser))
    html.groupList(groups = userGroups)
  }

  def groupNew: Html = {
    html.groupNew()
  }

  case class GroupModel(name: String, readRight: Boolean = false, updateRight: Boolean = false, deleteRight: Boolean = false , members: Seq[Member])
  case class Member(id: String, name: String)


  def groupLoad(user: String, name: String): Result = {
    // TODO
    Ok
  }

  def groupSubmit(user: String, name: String, data: String): Result = {

    println(data)
    println(in[GroupModel](data))

    val model: GroupModel = in[GroupModel](data)
    println(model)

    println(out[GroupModel](model))

    Ok
  }

}