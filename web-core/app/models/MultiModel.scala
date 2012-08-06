package models

import _root_.util.DomainConfigurationHandler
import com.novus.salat.dao.SalatDAO
import com.novus.salat
import models.mongoContext._
import com.mongodb.casbah.Imports._
import play.api.Logger

/**
 * Multi-tenancy capable model. Implementing model objects get a chance to work with several databases depending
 * on the current configuration.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait MultiModel[A <: salat.CaseClass, B <: SalatDAO[A, ObjectId]] {

  private lazy val multiDAOs: Map[DomainConfiguration, B] = {
    DomainConfigurationHandler.domainConfigurations.map {
      config => {
        val connection = mongoConnections(config)
        val collection = connection(connectionName)
        initIndexes(collection)
        val dao = initDAO(collection, connection)
        (config -> dao)
      }
    }.toMap
  }

  protected def connectionName: String

  protected def initIndexes(collection: MongoCollection)

  protected def initDAO(collection: MongoCollection, connection: MongoDB): B

  def dao(implicit configuration: DomainConfiguration): B = multiDAOs.get(configuration).getOrElse {
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
