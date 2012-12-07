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

package models {

import com.mongodb.casbah.Imports.{ MongoCollection, MongoDB }
import com.mongodb.casbah.query.Imports._
import java.util.Date
import com.novus.salat.dao.SalatDAO
import java.io.File
import play.api.Play
import play.api.Play.current

package object dos extends MongoContext {

  def getNode = Play.configuration.getString("cultureHub.nodeName").getOrElse("defaultDosNode")

}

package dos {

case class Log(
  _id: ObjectId = new ObjectId,
  orgId: String,
  task_id: ObjectId,
  date: Date = new Date,
  node: String,
  message: String,
  taskType: TaskType, // saved here for redundancy
  sourceItem: Option[String] = None,
  resultItem: Option[String] = None, // file path or URL or ID to a single item that was processed, if applicable
  level: LogLevel = LogLevel.INFO
)

object Log extends MultiModel[Log, LogDAO] {

  protected def connectionName: String = "Logs"

  protected def initIndexes(collection: MongoCollection) {}

  protected def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: DomainConfiguration): LogDAO = new LogDAO(collection)
}

class LogDAO(collection: MongoCollection) extends SalatDAO[Log, ObjectId](collection)

case class LogLevel(name: String)

object LogLevel {
  val INFO = LogLevel("info")
  val ERROR = LogLevel("error")
  val values = List(INFO, ERROR)

  def valueOf(what: String) = values find { _.name == what }
}

case class Task(
  _id: ObjectId = new ObjectId,
  node: String,
  orgId: String,
  path: String,
  taskType: TaskType,
  params: Map[String, String] = Map.empty[String, String],
  queuedAt: Date = new Date,
  startedAt: Option[Date] = None,
  finishedAt: Option[Date] = None,
  state: TaskState = TaskState.QUEUED,
  totalItems: Int = 0,
  processedItems: Int = 0
) {

  def pathAsFile = new File(path)

  def pathExists = new File(path).exists()

  def isCancelled = Task.dao(orgId).findOne(MongoDBObject("_id" -> _id, "state.name" -> TaskState.CANCELLED.name)).isDefined

  override def toString = "Task[%s] type: %s, path: %s, params: %s".format(_id, taskType.name, path, params.toString)

}

object Task extends MultiModel[Task, TaskDAO] {

  protected def connectionName: String = "Tasks"

  protected def initIndexes(collection: MongoCollection) {}

  protected def initDAO(collection: MongoCollection, connection: MongoDB)(implicit configuration: DomainConfiguration): TaskDAO = new TaskDAO(collection)
}

class TaskDAO(collection: MongoCollection) extends SalatDAO[Task, ObjectId](collection) {

  def list(taskType: TaskType) = find(MongoDBObject("taskType.name" -> taskType.name)).toList

  def list(state: TaskState) = find(MongoDBObject("state.name" -> state.name, "node" -> getNode)).sort(MongoDBObject("queuedAt" -> 1)).toList

  def listAll() = find(MongoDBObject()).sort(MongoDBObject("queuedAt" -> 1)).toList

  def start(task: Task) {
    update(MongoDBObject("_id" -> task._id), $set("state.name" -> TaskState.RUNNING.name, "startedAt" -> new Date))
  }

  def finish(task: Task) {
    update(MongoDBObject("_id" -> task._id), $set("state.name" -> TaskState.FINISHED.name, "finishedAt" -> new Date))
  }

  def cancel(task: Task) {
    update(MongoDBObject("_id" -> task._id), $set("state.name" -> TaskState.CANCELLED.name, "finishedAt" -> new Date))
  }

  def setTotalItems(task: Task, total: Int) {
    update(MongoDBObject("_id" -> task._id), $set("totalItems" -> total))
  }

  def incrementProcessedItems(task: Task, amount: Int) {
    update(MongoDBObject("_id" -> task._id), $inc("processedItems" -> amount))
  }

}

case class TaskType(name: String)

object TaskType {
  val THUMBNAILS_CREATE = TaskType("createThumbnails")
  val THUMBNAILS_DELETE = TaskType("deleteThumbnails")
  val NORMALIZE = TaskType("normalize")
  val TILES = TaskType("tiles")
  val values = List(THUMBNAILS_CREATE, THUMBNAILS_DELETE, NORMALIZE, TILES)

  def valueOf(what: String) = values find { _.name == what }
}

case class TaskState(name: String)

object TaskState {
  val QUEUED = TaskState("queued")
  val RUNNING = TaskState("running")
  val FINISHED = TaskState("finished")
  val CANCELLED = TaskState("cancelled")
  val values = List(QUEUED, RUNNING, FINISHED, CANCELLED)

  def valueOf(what: String) = values find { _.name == what }

}

}

}