package models

import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import models.mongoContext._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class RouteAccess(_id: ObjectId = new ObjectId,
                       route: String,
                       queryParams: List[String])

object RouteAccess extends SalatDAO[RouteAccess, ObjectId](routeAccessCollection)