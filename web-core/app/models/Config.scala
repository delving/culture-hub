package models

import HubMongoContext._
import com.novus.salat.dao.SalatDAO
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject
import java.util.Date
import com.mongodb.casbah.Imports._

/**
 * Configuration stored in a mongo database
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
case class Config(
  _id: ObjectId = new ObjectId,
  lastModified: Date = new Date(),
  orgId: String,
  rawConfiguration: String,
  errors: Seq[String] = Seq.empty)

object Config extends SalatDAO[Config, ObjectId](configurationCollection) {

  def findAll: Seq[Config] = find(MongoDBObject()).toSeq

  def findOneByOrgId(orgId: String): Option[Config] = findOne(MongoDBObject("orgId" -> orgId))

  def addErrors(orgId: String, errors: Seq[String]) {
    findOneByOrgId(orgId) map { config =>
      save(
        config.copy(errors = errors)
      )
    }
  }

  def clearErrors(orgId: String) {
    findOneByOrgId(orgId) map { config =>
      save(
        config.copy(errors = Seq.empty)
      )
    }
  }

}