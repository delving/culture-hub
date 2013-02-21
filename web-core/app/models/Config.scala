package models

import HubMongoContext._
import com.novus.salat.dao.SalatDAO
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject

/**
 * Configuration stored in a mongo database
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
case class Config(orgId: String, rawConfiguration: String, error: Option[String] = None)

object ConfigDAO extends SalatDAO[Config, ObjectId](configurationCollection) {

  def findAll: Seq[Config] = find(MongoDBObject()).toSeq
}