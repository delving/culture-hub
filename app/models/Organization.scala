package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import com.mongodb.casbah.commons.MongoDBObject

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Organization(_id: ObjectId = new ObjectId,
                        node: String, // node on which this organization runs
                        orgId: String, // identifier of this organization, unique in the world, used in the URL
                        name: Map[String, String] = Map.empty[String, String]) // language - orgName

object Organization extends SalatDAO[Organization, ObjectId](organizationCollection) {

  def isOwner(user: ObjectId) = Group.count(MongoDBObject("users" -> user, "grantType" -> GrantType.OWN)) > 0
}

case class Group(_id: ObjectId = new ObjectId,
                 node: String,
                 name: String,
                 orgId: String,
                 grantType: GrantType,
                 dataSets: List[ObjectId] = List.empty[ObjectId],
                 users: List[ObjectId] = List.empty[ObjectId])

object Group extends SalatDAO[Group, ObjectId](groupCollection) {

  /** lists all groups a user has access to for a given organization **/
  def list(user: ObjectId, orgId: String) = {
    if(Organization.isOwner(user)) {
      Group.find(MongoDBObject("orgId" -> orgId))
    } else {
      Group.find(MongoDBObject("users" -> user, "orgId" -> orgId))
    }
  }

}

case class GrantType(value: Int)
object GrantType {
  def illegal(value: Int) = throw new IllegalArgumentException("Illegal value %s for GrantType".format(value))
  val VIEW = GrantType(0)
  val MODIFY = GrantType(10)
  val OWN = GrantType(42)
  val values: Map[Int, String] = Map(VIEW.value -> "view", MODIFY.value -> "modify", OWN.value -> "own")
  def name(value: Int): String = values.get(value).getOrElse(illegal(value))
  def name(gt: GrantType): String = values.get(gt.value).getOrElse(illegal(gt.value))
  def get(value: Int) = {
    if(!values.contains(value)) illegal(value)
    GrantType(value)
  }

}