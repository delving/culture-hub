package models

import models.HubMongoContext._
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO
import core.node.Node

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
case class HubNode(
    _id: ObjectId = new ObjectId,
    nodeId: String,
    name: String,
    orgId: String,
    contacts: Seq[String] = Seq.empty) extends Node {

  val isLocal: Boolean = true

  override def equals(other: Any): Boolean = {
    other.isInstanceOf[Node] && other.asInstanceOf[Node].nodeId == nodeId
  }

  override def hashCode(): Int = nodeId.hashCode
}

object HubNode extends MultiModel[HubNode, HubNodeDAO] {

  protected def connectionName: String = "HubNode"

  protected def initIndexes(collection: MongoCollection) {
    addIndexes(collection, Seq(MongoDBObject("nodeId" -> 1)))
  }

  protected def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: OrganizationConfiguration): HubNodeDAO = {
    new HubNodeDAO(collection)
  }

  def addContact(to: HubNode, contact: Node)(implicit configuration: OrganizationConfiguration) {
    if (!to.contacts.exists(contact.nodeId == _)) {
      val updated = to.copy(contacts = to.contacts ++ Seq(contact.nodeId))
      dao.save(updated)
    }
  }

}

class HubNodeDAO(collection: MongoCollection) extends SalatDAO[HubNode, ObjectId](collection) {

  def findAll = find(MongoDBObject()).toSeq

  def findOne(node: Node): Option[HubNode] = findOne(MongoDBObject("nodeId" -> node.nodeId))

  def findOne(nodeId: String): Option[HubNode] = findOne(MongoDBObject("nodeId" -> nodeId))

}

