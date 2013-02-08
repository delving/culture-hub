package libs

import at.ait.dme.magicktiler.MagickTiler
import at.ait.dme.magicktiler.ptif.PTIFConverter
import at.ait.dme.magicktiler.image.ImageFormat
import java.io.File
import play.api.Logger

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

  def createTile(tilesWorkingBasePath: File, tilesOutputPath: File, sourceImage: File) {
    def targetName = (if (sourceImage.getName.indexOf(".") > 0)
      sourceImage.getName.substring(0, sourceImage.getName.lastIndexOf("."))
    else
      sourceImage.getName) + ".tif"

    def targetFile = new File(tilesOutputPath, targetName)
    targetFile.createNewFile()

    val tileInfo = getTiler(tilesWorkingBasePath).convert(sourceImage, targetFile)

    log.info("Created PTIF tile for image %s, %s zoom levels".format(sourceImage.getName, tileInfo.getZoomLevels))
  }

}
