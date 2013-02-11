package models

import _root_.util.OrganizationConfigurationHandler
import com.novus.salat.dao.SalatDAO
import com.novus.salat
import models.HubMongoContext._
import com.mongodb.casbah.Imports._
import play.api.Logger

/**
 * Multi-tenancy capable model. Implementing model objects get a chance to work with several databases depending
 * on the current configuration.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait MultiModel[A <: salat.CaseClass, B <: SalatDAO[A, ObjectId]] {

  private lazy val multiDAOs: Map[OrganizationConfiguration, B] = {
    OrganizationConfigurationHandler.organizationConfigurations.map {
      config =>
        {
          val connection = mongoConnections(config)
          val collection = connection(connectionName)
          collection.setWriteConcern(WriteConcern.FsyncSafe)
          initIndexes(collection)
          val dao = initDAO(collection, connection)(config)
          (config -> dao)
        }
    }.toMap
  }

  protected def connectionName: String

  protected def initIndexes(collection: MongoCollection)

  protected def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: OrganizationConfiguration): B

  def dao(implicit configuration: OrganizationConfiguration): B = multiDAOs.get(configuration).getOrElse {
    Logger("CultureHub").error("No DAO for configuration " + configuration.name)
    throw new RuntimeException("No DAO for domain " + configuration.name)
  }

  def dao(orgId: String): B = multiDAOs.find(_._1.orgId == orgId).map(_._2).getOrElse {
    Logger("CultureHub").error("No DAO for orgId " + orgId)
    throw new RuntimeException("No DAO for orgId " + orgId)
  }

  def all: Seq[B] = multiDAOs.values.toSeq

  def byConfiguration = multiDAOs

}
