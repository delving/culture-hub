package eu.delving {
package model {

import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import net.liftweb.util.Helpers._
import lib._
import xml.{Node, NodeSeq}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{Extraction, Xml}
import net.liftweb.http.rest.JsonXmlAble


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
  def getCase = UserCase(firstName.asString, lastName.asString, email.asString)

}

case class UserCase(firstName: String, lastName: String, email: String) extends JsonXmlAble

}

}
