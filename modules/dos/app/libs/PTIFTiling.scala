package libs

import at.ait.dme.magicktiler.{ TilesetInfo, MagickTiler }
import at.ait.dme.magicktiler.ptif.PTIFConverter
import at.ait.dme.magicktiler.image.ImageFormat
import java.io.File
import play.api.Logger
import org.apache.commons.io.FileUtils

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object PTIFTiling {

  val log = Logger("CultureHub")

  def getTiler(tilesWorkingBasePath: File) = {
    val tiler: MagickTiler = new PTIFConverter()
    tiler.setWorkingDirectory(tilesWorkingBasePath)
    tiler.setTileFormat(ImageFormat.JPEG)
    tiler.setJPEGCompressionQuality(75)
    tiler.setBackgroundColor("#ffffffff")
    tiler.setGeneratePreviewHTML(false)
    tiler
  }

  def createTile(tilesWorkingBasePath: File, tilesOutputPath: File, sourceImage: File): Either[String, TilesetInfo] = {

    try {
      def targetName = (if (sourceImage.getName.indexOf(".") > 0)
        sourceImage.getName.substring(0, sourceImage.getName.lastIndexOf("."))
      else
        sourceImage.getName) + ".tif"

      def targetFile = new File(tilesOutputPath, targetName)
      log.debug("Target file: " + targetFile.getAbsolutePath)

      if (targetFile.exists()) {
        log.debug(s"DeepZoom tile already exists for ${targetFile.getAbsolutePath}, so we delete it")
        FileUtils.deleteQuietly(targetFile)

      }
      targetFile.createNewFile()

      val tileInfo = getTiler(tilesWorkingBasePath).convert(sourceImage, targetFile)
      log.info("Created PTIF tile for image %s, %s zoom levels".format(sourceImage.getName, tileInfo.getZoomLevels))
      Right(tileInfo)
    } catch {
      case t: Throwable =>
        log.error(s"Failed to create PTIF tile for image ${sourceImage.getName}", t)
        Left(t.getMessage)
    }
  }

}