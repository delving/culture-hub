package models

import models.HubMongoContext._
import core.Node
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
case class VirtualNode(
  _id: ObjectId = new ObjectId,
  nodeId: String,
  name: String,
  orgId: String) extends Node {

  val isLocal: Boolean = true

}

object VirtualNode extends MultiModel[VirtualNode, VirtualNodeDAO] {

  protected def connectionName: String = "VirtualNode"

  protected def initIndexes(collection: MongoCollection) {
    addIndexes(collection, Seq(MongoDBObject("nodeId" -> 1)))
  }

  protected def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: DomainConfiguration): VirtualNodeDAO = {
    new VirtualNodeDAO(collection)
  }

}

class VirtualNodeDAO(collection: MongoCollection) extends SalatDAO[VirtualNode, ObjectId](collection) {

  def findAll = find(MongoDBObject()).toSeq

  def findOne(orgId: String, nodeId: String): Option[VirtualNode] = findOne(MongoDBObject("orgId" -> orgId, "nodeId" -> nodeId))

}

