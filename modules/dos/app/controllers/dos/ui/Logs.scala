package controllers.dos.ui

import models.dos._
import com.mongodb.casbah.Imports._
import play.api.mvc._
import extensions.Extensions
import com.novus.salat.dao.SalatMongoCursor
import controllers.OrganizationConfigurationAware

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Logs extends Controller with Extensions with OrganizationConfigurationAware {

  def list(taskId: ObjectId, lastCount: Option[Int]) = OrganizationConfigured {
    Action {
      implicit request =>
        val cursor: SalatMongoCursor[Log] = Log.dao.find(MongoDBObject("task_id" -> taskId)).limit(500).sort(MongoDBObject("date" -> 1))
        val (logs, skipped) = if (lastCount != None && lastCount.get > 0) {
          if (cursor.count - lastCount.get > 100) {
            (cursor.skip(cursor.count - 100), true)
          } else {
            (cursor.skip(lastCount.get + 1), false)
          }
        } else {
          if (cursor.count > 100) {
            (cursor.skip(cursor.count - 100), true)
          } else {
            (cursor, false)
          }
        }
        Json(Map("logs" -> logs.toList, "skipped" -> skipped))
    }
  }

  def view(taskId: ObjectId) = OrganizationConfigured {
    Action {
      implicit request =>
        {
          val cursor: SalatMongoCursor[Log] = Log.dao.find(MongoDBObject("task_id" -> taskId)).sort(MongoDBObject("date" -> 1))
          Ok(cursor.map(log => log.date + "\t" + s"[${log.orgId}] " + log.level.name.toUpperCase + "\t" + log.node + "\t" + log.message).mkString("\n"))
        }
    }
  }

}