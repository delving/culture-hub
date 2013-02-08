/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.dos.ui

import models.dos.{TaskType, TaskState, Task}
import extensions.Extensions
import TaskState._
import org.bson.types.ObjectId
import play.api.mvc._
import play.api.Play
import play.api.Logger
import play.api.Play.current
import eu.delving.templates.scala.GroovyTemplates
import controllers.OrganizationConfigurationAware

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Tasks extends Controller with Extensions with OrganizationConfigurationAware with GroovyTemplates {

  def add(path: String, taskType: String) = OrganizationConfigured {
    Action(parse.tolerantFormUrlEncoded) {
      implicit request =>

        val tt = TaskType.valueOf(taskType)
        if (tt.isEmpty) {
          val msg = "Invalid task type " + taskType
          Logger("DoS").error(msg)
          InternalServerError(msg)
        } else {
          val taskParams: Map[String, Seq[String]] = request.body
          val task = Task(orgId = configuration.orgId, node = getNode, path = path, taskType = tt.get, params = taskParams.map(e => (e._1, e._2.head)).toMap)
          Logger("DOS").info("Adding new task to queue: " + task.toString)
          Task.dao.insert(task) match {
            case None => InternalServerError("Could not create da task")
            case Some(taskId) => Json(task.copy(_id = taskId))
          }
        }
    }
  }

  def cancel(id: ObjectId) = OrganizationConfigured {
    Action {
      implicit request =>
        val task = Task.dao.findOneById(id)
        if (task.isEmpty)
          NotFound("Could not find task with id " + id)
        else {
          Task.dao.cancel(task.get)
          Ok
        }
    }
  }

  def list(what: String) = OrganizationConfigured {
    Action {
      implicit request =>
        val tasks = TaskState.valueOf(what) match {
          case Some(state) if (state == QUEUED || state == RUNNING || state == FINISHED || state == CANCELLED) => Some(Task.dao.list(state))
          case None => None
        }
        if (tasks == None)
          InternalServerError("Invalid task state " + what)
        else
          Json(Map("tasks" -> tasks.get))
    }
  }

  def listAll() = OrganizationConfigured {
    Action {
      implicit request =>
        Json(Map("running" -> Task.dao.list(RUNNING), "queued" -> Task.dao.list(QUEUED), "finished" -> Task.dao.list(FINISHED)))
    }
  }

  def status(id: ObjectId) = OrganizationConfigured {
    Action {
      implicit request =>
        val task = Task.dao.findOneById(id)
        if (task.isEmpty) NotFound("Could not find task with id " + id)
        else
          Json(
            Map(
              "totalItems" -> task.get.totalItems,
              "processedItems" -> task.get.processedItems,
              "percentage" -> ((task.get.processedItems.toDouble / task.get.totalItems) * 100).round
            ))
    }
  }

  private def getNode = Play.configuration.getString("cultureHub.nodeName").getOrElse("defaultDosNode")

}