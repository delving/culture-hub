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

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoDB, MongoConnection}
import java.util.Date
import com.novus.salat.dao.SalatDAO
import org.bson.types.ObjectId
import java.io.File
import play.api.Play
import play.api.Play.current

package object dos extends MongoContext {

  val connectionName = if(Play.isTest) "dosTest" else "dos"

  val connection: MongoDB = createConnection(connectionName)
  val taskCollection = connection("Tasks")
  val logCollection = connection("Logs")
  val originCollection = connection("Files")

  def getNode = Play.configuration.getString("cultureHub.nodeName").getOrElse("defaultDosNode")

}

package dos {

import java.net.URL

case class Log(_id: ObjectId = new ObjectId,
               task_id: ObjectId,
               date: Date = new Date,
               node: String,
               message: String,
               taskType: TaskType, // saved here for redundancy
               sourceItem: Option[String] = None,
               resultItem: Option[String] = None, // file path or URL or ID to a single item that was processed, if applicable
               level: LogLevel = LogLevel.INFO)

object Log extends SalatDAO[Log, ObjectId](collection = logCollection)

case class LogLevel(name: String)

object LogLevel {
  val INFO = LogLevel("info")
  val ERROR = LogLevel("error")
  val values = List(INFO, ERROR)

  def valueOf(what: String) = values find { _.name == what }
}

case class Task(_id: ObjectId = new ObjectId,
                node: String,
                path: String,
                taskType: TaskType,
                params: Map[String, String] = Map.empty[String, String],
                queuedAt: Date = new Date,
                startedAt: Option[Date] = None,
                finishedAt: Option[Date] = None,
                state: TaskState = TaskState.QUEUED,
                totalItems: Int = 0,
                processedItems: Int = 0) {

  def pathAsFile = new File(path)

  def pathExists = new File(path).exists()

  def isCancelled = taskCollection.findOne(MongoDBObject("_id" -> _id, "state.name" -> TaskState.CANCELLED.name)).isDefined

  override def toString = "Task[%s] type: %s, path: %s, params: %s".format(_id, taskType.name, path, params.toString)

}

object Task extends SalatDAO[Task, ObjectId](collection = taskCollection) {
  def list(taskType: TaskType) = Task.find(MongoDBObject("taskType.name" -> taskType.name)).toList

  def list(state: TaskState) = Task.find(MongoDBObject("state.name" -> state.name, "node" -> getNode)).sort(MongoDBObject("queuedAt" -> 1)).toList

  def listAll() = Task.find(MongoDBObject()).sort(MongoDBObject("queuedAt" -> 1)).toList

  def start(task: Task) {
    Task.update(MongoDBObject("_id" -> task._id), $set("state.name" -> TaskState.RUNNING.name, "startedAt" -> new Date))
  }

  def finish(task: Task) {
    Task.update(MongoDBObject("_id" -> task._id), $set("state.name" -> TaskState.FINISHED.name, "finishedAt" -> new Date))
  }

  def cancel(task: Task) {
    Task.update(MongoDBObject("_id" -> task._id), $set("state.name" -> TaskState.CANCELLED.name, "finishedAt" -> new Date))
  }

  def setTotalItems(task: Task, total: Int) {
    Task.update(MongoDBObject("_id" -> task._id), $set("totalItems" -> total))
  }

  def incrementProcessedItems(task: Task, amount: Int) {
    Task.update(MongoDBObject("_id" -> task._id), $inc("processedItems" -> amount))
  }

}

case class TaskType(name: String)

object TaskType {
  val THUMBNAILS_CREATE = TaskType("createThumbnails")
  val THUMBNAILS_DELETE = TaskType("deleteThumbnails")
  val FLATTEN = TaskType("flatten")
  val TILES = TaskType("tiles")
  val values = List(THUMBNAILS_CREATE, THUMBNAILS_DELETE, FLATTEN, TILES)

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

case class SourceType(name: String)
object SourceType {
  val FILE = SourceType("file")
  val URL = SourceType("url")
  val values = List(FILE, URL)
  def valueOf(what: String) = values find { _.name == what }
}

case class FileOrigin(_id: ObjectId = new ObjectId,
                      origin: String, // file-system path or URL
                      sourceType: SourceType,
                      lastModified: Date,
                      size: Long) {
  /**
   * Retrieves the source
   */
  def sourceFile: Option[File] = {
    if(sourceType != SourceType.FILE) {
      None
    } else {
      val f = new File(origin)
      if(!f.exists()) {
        None
      } else {
        Some(f)
      }
    }
  }

  /**
   * Whether the file origin is up-to-date
   */
  def upToDate: Boolean = sourceType match {
    case SourceType.FILE => sourceFile.isDefined && sourceFile.get.lastModified() == lastModified.getTime
    case _ => false
  }

  /**
   * Updates the file origin meta-data
   */
  def update {
    sourceType match {
      case SourceType.FILE if(sourceFile.isDefined) =>
        val f = sourceFile.get
        val updated = this.copy(lastModified = new Date(f.lastModified()), size = f.length())
        FileOrigin.save(updated)

        // TODO update GM identify, EXIM etc.

      case _ =>
    }
  }

  /**
   * Adds a link to a file that this origin is related to
   */
  def addLink(key: String, origin: Map[String, String]) {}

}

object FileOrigin extends SalatDAO[FileOrigin, ObjectId](collection = originCollection) {

  /**
   * Creates the file origin given a File, returns <code>false</code> if it wasn't created
   */
  def create(file: File): Boolean = {
    if (FileOrigin.findOne(MongoDBObject("origin" -> file.getAbsolutePath)) != None) {
      return false
    }

    val origin = FileOrigin(origin = file.getAbsolutePath,
      sourceType = SourceType.FILE,
      lastModified = new Date(file.lastModified()),
      size = file.length())

    FileOrigin.insert(origin) match {
      case Some(id) =>
        val fileOrigin = origin.copy(_id = id)
        // TODO if this is an image, do GM analysis and EXIM and whatsonot
        true
      case None => false
    }
  }

  def create(url: URL): Boolean = { false } // TODO implement

}

}


}
