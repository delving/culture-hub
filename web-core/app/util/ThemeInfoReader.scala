package util

import play.Play
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import play.api.Play.current
import play.api.{Logger, PlayException}
import java.lang.String
import extensions.MissingLibs

/**
 * Handler for display theme information
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object ThemeInfoReader {

  val THEMES_ROOT = "/public/themes/"

  var cache = new ConcurrentHashMap[String, Properties]()

  def get(key: String, theme: String, themeDir: String): Option[String] = {

    val mayInfo = if (Play.isProd) {
      val themeInfo = cache.get(theme)
      if (themeInfo != null) {
        Right(themeInfo)
      } else {
        getInfo(key, theme, themeDir)
      }
    } else {
      getInfo(key, theme, themeDir)
    }
    val info = mayInfo match {
      case Right(i) => cache.put(theme, i); i
      case Left(err) => throw new PlayException("Configuration exception", err)
    }

    info.getProperty(key) match {
      case value if value == null || value.trim().length() == 0 => None
      case v@_ => Some(v)
    }
  }

  private[util] def getInfo(key: String, theme: String, themeDir: String): Either[String, Properties] = {
    val infoPath: String = THEMES_ROOT + themeDir + "/info.conf"
    val info = current.resourceAsStream(infoPath)
    if (info.isDefined) Right(MissingLibs.readUtf8Properties(info.get))
    else {
      val message = "Could not file info.conf files for theme %s at %s".format(theme, infoPath)
      Logger("CultureHub").error(message)
      Left(message)
    }
  }

}