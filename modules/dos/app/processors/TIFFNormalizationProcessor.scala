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
import org.im4java.process.OutputConsumer
import org.im4java.core.{ImageCommand, IMOperation}
import java.io.{File, InputStreamReader, BufferedReader, InputStream}
import libs.Normalizer
import org.apache.commons.io.FileUtils

/**
 * This processor normalizes original TIFs, so that tiling works nicely with it. Original images are moved to a new subdirectory called "_original"
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object TIFFNormalizationProcessor extends Processor {

  def process(task: Task, processorParams: Map[String, AnyRef]) {

    val originalDir = new File(task.pathAsFile, "_original")
    val workDir = new File(task.pathAsFile, "_temp")
    originalDir.mkdir()
    workDir.mkdir()

    val images = task.pathAsFile.listFiles().filter(f => isImage(f.getName))
    Task.dao(task.orgId).setTotalItems(task, images.size)

    for (i <- images; if (!task.isCancelled)) {
      Normalizer.normalize(i, workDir).map { file =>
        i.renameTo(new File(originalDir, i.getName))
        file.renameTo(new File(task.pathAsFile, i.getName))
        info(task, """Image %s normalized succesfully, moved original to directory "_original"""".format(i.getName), Some(i.getAbsolutePath), Some(file.getAbsolutePath))
      }
      Task.dao(task.orgId).incrementProcessedItems(task, 1)
    }

    FileUtils.deleteDirectory(workDir)

  }
}