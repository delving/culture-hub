package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Organization(_id: ObjectId = new ObjectId,
                        node: String, // node on which this organization runs
                        orgName: String, // identifier of this organization, unique in the world, used in the URL
                        name: Map[String, String] = Map.empty[String, String]) // language - orgName

object Organization extends SalatDAO[Organization, ObjectId](organizationCollection)

case class Group(_id: ObjectId = new ObjectId,
                 node: String,
                 name: String,
                 org_id: ObjectId,
                 grantType: GrantType,
                 dataSets: List[ObjectId],
                 users: List[ObjectId])

object Group extends SalatDAO[Group, ObjectId](groupCollection)

case class GrantType(value: Int)
object GrantType {
  val VIEW = GrantType(0)
  val MODIFY = GrantType(10)
  val OWN = GrantType(20)
  val values: Map[Int, String] = Map(VIEW.value -> "view", MODIFY.value -> "modify", OWN.value -> "own")
  def name(value: Int): String = values.get(value).getOrElse(throw new IllegalArgumentException("Illegal value %s for GrantType".format(value)))
  def get(value: Int) = {
    if(!values.contains(value)) throw new IllegalArgumentException("Illegal value %s for GrantType".format(value))
    GrantType(value)
  }

}