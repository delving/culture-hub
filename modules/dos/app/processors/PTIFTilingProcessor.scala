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

import at.ait.dme.magicktiler.MagickTiler
import models.dos.Task
import java.io.File
import util.OrganizationConfigurationHandler
import libs.PTIFTiling

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object PTIFTilingProcessor extends Processor {

  def process(task: Task, processorParams: Map[String, AnyRef]) {

    val configuration = OrganizationConfigurationHandler.getByOrgId(task.orgId)

    val tilesOutputBasePath = new File(configuration.objectService.tilesOutputBaseDir)
    val tilesWorkingBasePath = new File(configuration.objectService.tilesWorkingBaseDir)

    def checkOrCreate(dir: File) = dir.exists() || !dir.exists() && dir.mkdir()

    if (!checkOrCreate(tilesOutputBasePath)) {
      error(task, "Cannot find / create tiles output directory '%s'".format(tilesOutputBasePath.getAbsolutePath))
      return
    }
    if (!checkOrCreate(tilesWorkingBasePath)) {
      error(task, "Cannot find / create tiles working directory '%s'".format(tilesWorkingBasePath.getAbsolutePath))
      return
    }

    val p = new File(task.path)
    if (!p.exists()) {
      error(task, "Path '%s' does not exist or is unreachable".format(task.path))
    } else {

      val collectionId = task.params.get(controllers.dos.COLLECTION_IDENTIFIER_FIELD).getOrElse({
        error(task, "No spec passed for task " + task)
        return
      })

      val orgId = task.params.get(controllers.dos.ORGANIZATION_IDENTIFIER_FIELD).getOrElse({
        error(task, "No org passed for task " + task)
        return
      })

      val orgPath = new File(tilesOutputBasePath, orgId)
      if (!orgPath.exists() && !orgPath.mkdir()) {
        error(task, "Could not create tile org path " + orgPath.getAbsolutePath, Some(orgPath.getAbsolutePath))
        return
      }

      // output path = tiles base dir + task org name + task spec name
      val outputPath = new File(orgPath, collectionId)
      if (outputPath.exists() && outputPath.isDirectory) {
        error(task, "Output directory '%s' already exists, delete it first if you want to re-tile".format(outputPath.getAbsolutePath))
        return
      }
      if (!outputPath.mkdir()) {
        error(task, "Cannot create output directory '%s'".format(outputPath.getAbsolutePath))
        return
      }

      val tiler: MagickTiler = PTIFTiling.getTiler(tilesWorkingBasePath)

      val images = p.listFiles().filter(f => isImage(f.getName))
      Task.dao(task.orgId).setTotalItems(task, images.size)

      for (i <- images; if (!task.isCancelled)) {
        val targetFileName = getImageName(i.getName)
        val targetFile: File = new File(outputPath, targetFileName + ".tif")
        targetFile.createNewFile()

        try {
          val tileInfo = tiler.convert(i, targetFile)
          info(task, "Generated PTIF for file " + i.getName + ": " + tileInfo.getImageWidth + "x" + tileInfo.getImageHeight + ", " + tileInfo.getZoomLevels + " zoom levels", Some(i.getAbsolutePath), Some(targetFile.getAbsolutePath))
          Task.dao(task.orgId).incrementProcessedItems(task, 1)
        } catch {
          case t: Throwable => error(task, "Could not create tile for image '%s': %s".format(i.getAbsolutePath, t.getMessage), Some(i.getAbsolutePath))
        }
      }
    }
  }
}