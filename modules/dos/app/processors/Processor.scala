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

package processors

import util.{ OrganizationConfigurationHandler, Logging }
import models.dos.{ Task }
import play.api.Play
import play.api.Play.current

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Processor extends Logging {

  /**
   * Does its thing given a path and optional parameters. The path may or may not exist on the file system.
   */
  def process(task: Task, processorParams: Map[String, AnyRef] = Map.empty[String, AnyRef])

  def isImage(name: String) = name.contains(".") && !name.startsWith(".") && (
    name.split("\\.").last.toLowerCase match {
      case "jpg" | "tif" | "tiff" => true
      case _ => false
    })

  def getGMCommand(task: Task): Option[String] = {
    // this is needed because OS X won't run commands unless given the full path
    val gmCommand = Play.configuration.getString("dos.graphicsmagic.cmd")
    if (gmCommand == None) {
      error(task, "Could not find path to GraphicsMagick in application.conf under key 'dos.graphicsmagic.cmd'")
      None
    } else gmCommand
  }

  /** image name without extension **/
  def getImageName(name: String) = if (name.indexOf(".") > 0) name.substring(0, name.lastIndexOf(".")) else name

  protected def getStore(orgId: String) = {
    import controllers.dos.fileStore
    fileStore(OrganizationConfigurationHandler.getByOrgId(orgId))
  }

}