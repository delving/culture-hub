package util

import play.{Logger, Play}
import play.libs.IO
import java.io.{FileInputStream, File}
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import play.exceptions.ConfigurationException

/**
 * Handler for display theme information
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ThemeInfoReader {

  val THEMES_ROOT = Play.applicationPath + "/public/themes/"

  var cache = new ConcurrentHashMap[String, Properties]()

  def get(key: String, theme: String): Option[String] = {

    val mayInfo = if(Play.mode == Play.Mode.PROD) {
      val themeInfo = cache.get(theme)
      if(themeInfo != null) {
        Right(themeInfo)
      } else {
        getInfo(key, theme)
      }
    } else {
      getInfo(key, theme)
    }
    val info = mayInfo match {
          case Right(i) => cache.put(theme, i); i
          case Left(err) => throw new ConfigurationException(err)
    }

    info.getProperty(key) match {
      case value if value == null || value.trim().length() == 0 => None
      case v@_ => Some(v)
    }
  }

  private[util] def getInfo(key: String, theme: String): Either[String, Properties] = {
    val themeDir = new File(THEMES_ROOT + theme)
    if(!themeDir.exists()) {
      val message = "Trying to access property %s but themes directory %s does not exist".format(key, themeDir.getAbsolutePath)
      Logger.error(message)
      return Left(message)
    }

    val infoFile = new File(themeDir, "info.conf")
    if(!infoFile.exists()) {
      val message = "Could not file info.conf files for theme %s at %s".format(theme, infoFile.getAbsolutePath)
      Logger.error(message)
      return Left(message)
    }
    Right(IO.readUtf8Properties(new FileInputStream(infoFile)))
  }

}