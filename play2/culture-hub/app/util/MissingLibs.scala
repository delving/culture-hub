package util

import java.io.InputStream
import java.util.Properties

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object MissingLibs {

  // ~~~ play.libs.IO

  def readUtf8Properties(is: InputStream): Properties = {
    val properties = new Properties()
    try {
      properties.load(is)
      is.close()
    } catch {
      case e => throw new RuntimeException(e)
    }
    properties
  }

}