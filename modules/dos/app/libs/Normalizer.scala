package libs

import org.im4java.core.{ ConvertCmd, IdentifyCmd, IMOperation, ImageCommand }
import org.im4java.process.OutputConsumer
import java.io.{ File, InputStreamReader, BufferedReader, InputStream }
import play.api.{ Logger, Play }
import org.apache.commons.io.FileUtils

/**
 * Normalizes a TIF prior to tiling.
 * Here we add all sorts of tricks we need to do in order to produce tiles that are compatible with the IIP Image Server for PTIF tiling.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object Normalizer {

  /**
   * Normalizes a file to be usable for tiling and presentation for DeepZoom
   * @param sourceImage the source image to be normalized
   * @param targetDirectory the target directory to which the normalized file should be written to
   * @return an optional normalized file, if normalization took place
   */
  def normalize(sourceImage: File, targetDirectory: File): Option[File] = {

    var source: File = sourceImage
    val destination = new File(targetDirectory, sourceImage.getName)

    val log = Logger("CultureHub")

    val hasBeenNormalized = identifyLargestLayer(source) != None || !isRGB(source)

    identifyLargestLayer(source).map { index =>
      log.info("Image %s has multiple layers, normalizing to just one...".format(source.getName))
      val convertCmd = new ConvertCmd
      val convertOp = new IMOperation
      convertOp.addRawArgs(source.getAbsolutePath + "[%s]".format(index))
      convertOp.addRawArgs(destination.getAbsolutePath)
      convertCmd.run(convertOp)
      source = destination
    }

    if (!isRGB(source)) {
      log.info("Image %s isn't RGB encoded, converting...".format(source.getName))

      if (isGrayscale(source)) {
        log.info("Image %s is Greyscale, converting to CMYK first to get the right colorspace when converting back...".format(source.getName))
        // GraphicsMagick considers Grayscale to be a subset of RGB, so it won't change the type when converting directly to RGB
        // so we first go over to CMYK and then back to RGB
        convertColorspace(targetDirectory, source, destination, "CMYK")
        source = destination
      }

      convertColorspace(targetDirectory, source, destination, "RGB")
    }

    if (hasBeenNormalized) {
      Some(destination)
    } else {
      None
    }
  }

  private def convertColorspace(targetDirectory: File, source: File, destination: File, colorspace: String) {
    val converted = new File(targetDirectory, colorspace + "_" + source.getName)
    val convertCmd = new ConvertCmd
    val convertOp = new IMOperation
    convertOp.colorspace(colorspace)
    convertOp.addImage(source.getAbsolutePath)
    convertOp.addImage(converted.getAbsolutePath)
    convertCmd.run(convertOp)
    if (converted.exists()) {
      if (converted.getParentFile.getAbsolutePath == targetDirectory.getAbsoluteFile) {
        FileUtils.deleteQuietly(source)
      }
      FileUtils.moveFile(converted, destination)
    }
  }

  private def identifyLargestLayer(sourceImage: File): Option[Int] = {
    val identified = identify(sourceImage, { op => })
    if (identified.length > 1) {
      // gm identify gives us lines like this:
      // 2006-011.tif TIFF 1000x800+0+0 DirectClass 8-bit 3.6M 0.000u 0:01
      // we want to fetch the 1000x800 part and know which line is da biggest
      val largestLayer = identified.map { line =>
        val Array(width: Int, height: Int) = line.split(" ")(2).split("\\+")(0).split("x").map(Integer.parseInt(_))
        (width, height)
      }.zipWithIndex.foldLeft((0, 0), 0) { (r: ((Int, Int), Int), c: ((Int, Int), Int)) =>
        if (c._1._1 * c._1._2 > r._1._1 * r._1._2) c else r
      }
      val largestIndex = largestLayer._2
      Some(largestIndex)
    } else {
      None
    }
  }

  private def isRGB(sourceImage: File): Boolean = {
    val colorspace = identify(sourceImage, { _.format("%r") })
    colorspace.headOption.map(_.contains("RGB")).getOrElse(false)
  }

  private def isGrayscale(sourceImage: File): Boolean = {
    val colorspace = identify(sourceImage, { _.format("%r") })
    colorspace.headOption.map(_.contains("Grayscale")).getOrElse(false)
  }

  private def identify(sourceImage: File, addParameters: IMOperation => Unit): Seq[String] = {
    val identifyCmd = new IdentifyCmd(false)
    val identifyOp = new IMOperation
    identifyOp.addImage(sourceImage.getAbsolutePath)
    var identified: List[String] = List()
    identifyCmd.setOutputConsumer(new OutputConsumer() {
      def consumeOutput(is: InputStream) {
        val br = new BufferedReader(new InputStreamReader(is))
        identified = Stream.continually(br.readLine()).takeWhile(_ != null).toList
      }
    })
    identifyCmd.run(identifyOp)

    identified.toSeq
  }

}
