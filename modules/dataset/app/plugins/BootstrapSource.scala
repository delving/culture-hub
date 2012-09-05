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

class BootstrapSource(dataDirectory : File) {

    val targetRoot = new File(dataDirectory.getParentFile.getParentFile, "target")
    val targetDirectory = new File(targetRoot, dataDirectory.getName)

    def copyAndHash() {
        FileUtils.deleteQuietly(targetDirectory)
        Files.createDirectory(targetDirectory)
        dataDirectory.listFiles().foreach(file => Files.copyFile(file, new File(targetDirectory, file.getName)))
        targetDirectory.listFiles().foreach(file => Hasher.ensureFileHashed(file))
    }

    def dataSetName = dataDirectory.getName

    def fileList() = targetDirectory.listFiles()

    def fileNamesString() = fileList().map(file => file.getName).reduceLeft(_ + "\n" + _)

    def file(name: String): File =
        fileList().filter(file => file.getName.endsWith(name))
        .headOption.getOrElse(throw new RuntimeException)
}

object BootstrapSource {
    val here = new File(".")

    val baseDirectory = if (here.listFiles().exists(f => f.isDirectory && f.getName == "modules"))
        here
    else
        new File(here, "culture-hub")

    val bootstrapDirectory = new File(baseDirectory, "modules/dataset/conf/bootstrap")

    val files = bootstrapDirectory.listFiles()

    val sources = files.map(file => new BootstrapSource(file))
}
