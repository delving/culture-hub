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

import models.dos.Task
import play.api.Play
import play.api.Play.current
import org.apache.commons.io.FileUtils
import controllers.dos._
import java.io._
import org.im4java.process.{ErrorConsumer}
import org.im4java.core.{ImageCommand, IMOperation}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object GMThumbnailCreationProcessor extends ThumbnailCreationProcessor with Thumbnail {


  protected def createThumbnailsForSize(images: Seq[File], width: Int, task: Task, orgId: String, collectionId: String) {

    val gmCommand = getGMCommand(task) getOrElse (return)
    val gmCommandPath = new File(gmCommand)
    if(!gmCommandPath.exists()) {
      error(task, "Could not find GM executable at '%s'".format(gmCommand))
      return
    }

    val tmpDir = new File(Play.configuration.getString("dos.tmpDir").getOrElse("/tmp"))

    val thumbnailTmpDir = new File(tmpDir, "%s_%s".format(task.pathAsFile.getParentFile.getName, width))
    if(thumbnailTmpDir.exists()) {
      FileUtils.deleteDirectory(thumbnailTmpDir)
    }
    if (!thumbnailTmpDir.mkdir()) {
      error(task, "Could not create temporary templating directory '%s' while processing thumbnails of size '%s' for source directory '%s'".format(thumbnailTmpDir.getAbsolutePath, width, task.path))
      return
    }

    for(image <- images; if (!task.isCancelled)) {
      // we want JPG thumbnails
      val imageName = getImageName(image.getName) + ".jpg"
      val thumbnailFile = new File(thumbnailTmpDir, imageName)
      val cmd = new ImageCommand(gmCommand, "convert")
      var e: List[String] = List()
      cmd.setErrorConsumer(new ErrorConsumer() {
        def consumeError(is: InputStream) {
          val br = new BufferedReader(new InputStreamReader(is))
          e = Stream.continually(br.readLine()).takeWhile(_ != null).toList
        }
      })

      val resizeOperation = new IMOperation()
      resizeOperation.size(width, width)
      resizeOperation.addImage(image.getAbsolutePath)
      resizeOperation.resize(width, width)
      resizeOperation.p_profile("\"*\"")
      resizeOperation.addImage(thumbnailFile.getAbsolutePath)
      resizeOperation.units("PixelsPerInch")
      resizeOperation.resample(72)
      try {
        cmd.run(resizeOperation)
        if(thumbnailFile.exists()) {
          val imageName = getImageName(image.getName)
          val thumb = storeThumbnail(new BufferedInputStream(new FileInputStream(thumbnailFile)), image.getName, width, controllers.dos.fileStore, Map(
            ORIGIN_PATH_FIELD -> image.getAbsolutePath,
            IMAGE_ID_FIELD -> imageName,
            TASK_ID -> task._id,
            ORGANIZATION_IDENTIFIER_FIELD -> orgId,
            COLLECTION_IDENTIFIER_FIELD -> collectionId))
          info(task, "Created thumbnail of size '%s' for image '%s'".format(width, image.getAbsolutePath), Some(image.getAbsolutePath), Some(thumb._2.toString))
        } else {
          error(task, "Error creating thumbnail for image '%s': %s".format(image.getAbsolutePath, e.mkString("\n")), Some(image.getAbsolutePath))
        }
      } catch {
        case t =>
          t.printStackTrace()
          error(task, "Error creating thumbnail for image '%s': %s".format(image.getAbsolutePath, t.getMessage), Some(image.getAbsolutePath))
      } finally {
        Task.incrementProcessedItems(task, 1)
      }
    }

    FileUtils.deleteDirectory(thumbnailTmpDir)

    }

/*    val fileNameTemplate = thumbnailTmpDir.getAbsolutePath + "%F"

    val bc = new BatchConverter(BatchConverter.Mode.PARALLEL) {

      override def run(op: Operation, images: List[String], pattern: String) {
        val fpr = new FilenamePatternResolver(pattern);
        var i = 0;
        for (img <- images) {
          val cmd = new ConvertCmd()
          cmd.setCommand(gmCommand)
          cmd.setPID(i)
          i = i + 1
          val pt = cmd.getProcessTask(op, img, fpr. createName(img));
          println("dude!")
          execute(pt);
        }
      }

      override def terminated() {
        val failed = getFailedConversions.map(f => (images.get(f.getIndex), f))
        val succeeded = images.filterNot(failed.contains(_))

        succeeded foreach {
          f =>
            val thumnailFile = new File(thumbnailTmpDir, f.getName)
            val thumb = storeThumbnail(new BufferedInputStream(new FileInputStream(thumnailFile)), f.getName, width, controllers.dos.fileStore)
            info(task, "Created thumbnail of size '%s' for image '%s'".format(width, f.getAbsolutePath), Some(f.getAbsolutePath), Some(thumb._2.toString))
        }
        failed foreach {
          f =>
            error(task, "Error creating thumbnail for image '%s': %s".format(f._1.getAbsolutePath, f._2.getMessage), Some(f._1.getAbsolutePath))
        }
      }
    }
    try {
//      System.setProperty("IM4JAVA_TOOLPATH", gmCommandPath.getParentFile.getAbsolutePath)
      bc.run(resizeOperation, images.map(_.getAbsolutePath).toList, fileNameTemplate)
    } catch {
      case t => error(task, "Could not run batch conversion job for directory '%s'".format(task.path))
    } finally {
      FileUtils.deleteDirectory(thumbnailTmpDir)
    }
  }
  */

}