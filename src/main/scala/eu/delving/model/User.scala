package eu.delving.model

import _root_.net.liftweb.common._
import xml.NodeSeq
import net.liftweb.http.rest.JsonXmlAble
import eu.delving.lib.{MetaMegaProtoUser, MegaProtoUser}

/**
 * The singleton that has methods for accessing the database
 */
object User extends User with MetaMegaProtoUser[User] {

  override def collectionName = "users"

  // define the MongoDB collection name
  override def screenWrap = Full(<lift:surround with="default" at="content">
      <lift:bind/>
  </lift:surround>)

  //override def skipEmailValidation = true // uncomment this line to skip email validations

  override def localForm(user: User, ignorePassword: Boolean): NodeSeq = {
    val formXhtml: NodeSeq = {
      <tr>
        <td>{user.firstName.displayName}</td> <td>{user.firstName.toForm openOr ""}</td>
      </tr>
      <tr>
        <td>{user.lastName.displayName}</td> <td>{user.lastName.toForm openOr ""}</td>
      </tr>
      <tr>
        <td>{user.email.displayName}</td> <td>{user.email.toForm openOr ""}</td>
      </tr>
      <tr>
        <td>{user.locale.displayName}</td> <td>{user.locale.toForm openOr ""}</td>
      </tr>
      <tr>
        <td>{user.timezone.displayName}</td> <td>{user.timezone.toForm openOr ""}</td>
      </tr>
    }

    if (!ignorePassword)
      formXhtml ++ <tr>
        <td>{user.password.displayName}</td> <td>{user.password.toForm openOr ""}</td>
      </tr>
    else
      formXhtml
  }
}

/**
* A "User" class that includes first name, last name, password
*/
class User extends MegaProtoUser[User] {
  def meta = User // what's the "meta" server
  def getValue = {
    if (User.loggedIn_?)
      UserPrivate(firstName.value, lastName.value, email.value)
    else
      UserPublic(firstName.value, lastName.value)
  }
}

case class UserPublic(firstName: String, lastName: String) extends JsonXmlAble
case class UserPrivate(firstName: String, lastName: String, email: String) extends JsonXmlAble
