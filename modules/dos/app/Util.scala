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

package util {

  import play.api.Play
  import play.api.Play.current
  import models.dos.{ Log, LogLevel, Task }

  /**
   * Logger that traces everything in relation to tasks.
   * Log entries are "smart" - they both are human-readable and machine-readable so we can turn the log entries into events that help build the history of everything that has happened to a thing.
   *
   * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
   */

  trait Logging {

    def info(task: Task, message: String, sourceItem: Option[String] = None, resultItem: Option[String] = None) {
      log(task, message, LogLevel.INFO, sourceItem, resultItem)
    }

    def error(task: Task, message: String, sourceItem: Option[String] = None, resultItem: Option[String] = None) {
      log(task, message, LogLevel.ERROR, sourceItem, resultItem)
    }

    def log(task: Task, message: String, level: LogLevel = LogLevel.INFO, sourceItem: Option[String] = None, resultItem: Option[String] = None) {
      Log.dao(task.orgId).insert(
        Log(
          orgId = task.orgId,
          message = message,
          level = level,
          task_id = task._id,
          taskType = task.taskType,
          sourceItem = sourceItem,
          resultItem = resultItem,
          node = getNode)
      )
    }

    private def getNode = Play.configuration.getString("cultureHub.nodeName").getOrElse("defaultDosNode")

  }

}
