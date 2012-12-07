package libs

import org.im4java.core.{ConvertCmd, IdentifyCmd, IMOperation, ImageCommand}
import org.im4java.process.OutputConsumer
import java.io.{File, InputStreamReader, BufferedReader, InputStream}
import play.api.Play
import org.apache.commons.io.FileUtils

/**
 * Normalizes a TIF prior to tiling.
 * Here we add all sorts of tricks we need to do in order to produce tiles that are compatible with the IIP Image Server for PTIF tiling.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
object Normalizer {

  def normalize(sourceImage: File, targetDirectory: File): File = {

    var source: File = sourceImage

    identifyLargestLayer(source).map { index =>
      val converted = new File(targetDirectory, source.getName)
      val convertCmd = new ConvertCmd
      val convertOp = new IMOperation
      convertOp.addRawArgs(source.getAbsolutePath + "[%s]".format(index))
      convertOp.addRawArgs(converted.getAbsolutePath)
      convertCmd.run(convertOp)
      source = converted
    }

    if (!isRGB(source)) {
      val converted = new File(targetDirectory, "RGB_" + source.getName)
      val convertCmd = new ConvertCmd
      val convertOp = new IMOperation
      convertOp.colorspace("RGB")
      convertOp.addImage(source.getAbsolutePath)
      convertOp.addImage(converted.getAbsolutePath)
      convertCmd.run(convertOp)
      if (converted.exists()) {
        FileUtils.deleteQuietly(source)
        FileUtils.moveFile(converted, source)
      }
    }

    source
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

  private def identify(sourceImage: File, addParameters: IMOperation => Unit): Seq[String] = {
    val identifiyCmd = new IdentifyCmd
    val identifyOp = new IMOperation
    identifyOp.addImage(sourceImage.getAbsolutePath)
    var identified: List[String] = List()
    identifiyCmd.setOutputConsumer(new OutputConsumer() {
      def consumeOutput(is: InputStream) {
        val br = new BufferedReader(new InputStreamReader(is))
        identified = Stream.continually(br.readLine()).takeWhile(_ != null).toList
      }
    })
    identifiyCmd.run(identifyOp)

    identified.toSeq
  }

}
