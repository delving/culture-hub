package controllers.user

import play.templates.Html
import play.mvc.Before
import controllers.{UserAuthentication, Secure, DelvingController}
import play.mvc.results.Result
import models.UserGroup
import reflect.BeanInfo

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends DelvingController with UserAuthentication with Secure {

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

  @BeanInfo case class GroupModel(name: String, readRight: Boolean = false, updateRight: Boolean, deleteRight: Boolean , members: List[Member])
  @BeanInfo case class Member(id: String, name: String)


  def groupLoad(user: String, name: String): Result = {
    // TODO
    Ok
  }

  def groupSubmit(user: String, name: String, data: String): Result = {

    import sjson.json._
    import sjson.json.DefaultProtocol._

    val serializer = Serializer.SJSON

    println(data)

    val model: GroupModel = serializer.in[GroupModel](data)

    println(model)

    Ok
  }

}