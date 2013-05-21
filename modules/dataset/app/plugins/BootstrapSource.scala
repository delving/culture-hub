package plugins

import eu.delving.metadata.Hasher
import java.io.File
import play.api.libs.Files
import org.apache.commons.io.FileUtils

/**
 * Wrap the bootstrap directories so it can be used
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

class BootstrapSource(dataDirectory: File) {

  val org = "delving"
  val spec = dataDirectory.getName
  val targetRoot = new File(System.getProperty("java.io.tmpdir"), "sample")
  val targetDirectory = new File(targetRoot, dataDirectory.getName)

  init()

  def init() {
    FileUtils.deleteQuietly(targetDirectory)
    Files.createDirectory(targetDirectory)
    dataDirectory.listFiles().foreach(file => Files.copyFile(file, new File(targetDirectory, file.getName)))
    targetDirectory.listFiles().foreach(file => Hasher.ensureFileHashed(file))
  }

  def fileList() = targetDirectory.listFiles()

  def fileNamesString() = fileList().map(file => file.getName).reduceLeft(_ + "\n" + _)

  def file(name: String): File =
    fileList().filter(file => file.getName.endsWith(name))
      .headOption.getOrElse(throw new RuntimeException("Could not find " + name))
}

object BootstrapSource {

  val here = new File(".")

  val baseDirectory = if (here.listFiles().exists(f => f.isDirectory && f.getName == "conf")) {
    here
  } else {
    new File(here, "culture-hub")
  }

  val bootstrapDirectory = new File(baseDirectory, "modules/dataset/conf/bootstrap")

  val files = {
    bootstrapDirectory.listFiles().filter(_.isDirectory)
  }

  val sources = files.map(file => new BootstrapSource(file))
}