package models

import util.{ OrganizationConfigurationResourceHolder, OrganizationConfigurationHandler }
import com.novus.salat.dao.SalatDAO
import com.novus.salat
import models.HubMongoContext._
import com.mongodb.casbah.Imports._
import play.api.Logger
import reflect.ClassTag

/**
 * Multi-tenancy capable model. Implementing model objects get a chance to work with several databases depending
 * on the current configuration.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait MultiModel[A <: salat.CaseClass, B <: SalatDAO[A, ObjectId]] {

  lazy private val multiDAOs: OrganizationConfigurationResourceHolder[OrganizationConfiguration, B] = {
    val daos = new OrganizationConfigurationResourceHolder[OrganizationConfiguration, B](s"DAO ${getClass.getName}") {
      protected def resourceConfiguration(configuration: OrganizationConfiguration): OrganizationConfiguration = configuration

      protected def onAdd(resourceConfiguration: OrganizationConfiguration): Option[B] = {
        val connection = mongoConnections.getResource(resourceConfiguration)
        val collection = connection(connectionName)
        collection.setWriteConcern(WriteConcern.FsyncSafe)
        initIndexes(collection)
        Some(initDAO(collection, connection)(resourceConfiguration))
      }

      protected def onRemove(removed: B) {}
    }

    OrganizationConfigurationHandler.registerResourceHolder(daos)
    daos
  }

  protected def connectionName: String

  protected def initIndexes(collection: MongoCollection)

  protected def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: OrganizationConfiguration): B

  def dao(implicit configuration: OrganizationConfiguration, ct: ClassTag[B]): B = multiDAOs.getResource(configuration)

  def dao(orgId: String): B = multiDAOs.findResource(_._1.orgId == orgId).map(_._2).getOrElse {
    Logger("CultureHub").error("No DAO for orgId " + orgId)
    throw new RuntimeException("No DAO for orgId " + orgId)
  }

  def all: Seq[B] = multiDAOs.allResources

  def filter(p: (OrganizationConfiguration) => Boolean)(implicit ct: ClassTag[B]): Seq[B] = multiDAOs.allConfigurations.filter(p).map(multiDAOs.getResource(_))

}