package models

import models.HubMongoContext._
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO
import core.node.Node

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
case class VirtualNode(
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

object VirtualNode extends MultiModel[VirtualNode, VirtualNodeDAO] {

  protected def connectionName: String = "VirtualNode"

  protected def initIndexes(collection: MongoCollection) {
    addIndexes(collection, Seq(MongoDBObject("nodeId" -> 1)))
  }

  protected def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: DomainConfiguration): VirtualNodeDAO = {
    new VirtualNodeDAO(collection)
  }

  def addContact(to: VirtualNode, contact: Node)(implicit configuration: DomainConfiguration) {
    if (!to.contacts.exists(contact.nodeId == _)) {
      val updated = to.copy(contacts = to.contacts ++ Seq(contact.nodeId))
      dao.save(updated)
    }
  }

}

class VirtualNodeDAO(collection: MongoCollection) extends SalatDAO[VirtualNode, ObjectId](collection) {

  def findAll = find(MongoDBObject()).toSeq

  def findOne(node: Node): Option[VirtualNode] = findOne(MongoDBObject("nodeId" -> node.nodeId))

  def findOne(nodeId: String): Option[VirtualNode] = findOne(MongoDBObject("nodeId" -> nodeId))

}

