package models

import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import models.mongoContext._
import java.util.Date

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class RouteAccess(_id: ObjectId = new ObjectId,
                       date: Date = new Date(),
                       uri: String,
                       queryString: Map[String, Seq[String]])

object RouteAccess extends SalatDAO[RouteAccess, ObjectId](routeAccessCollection)