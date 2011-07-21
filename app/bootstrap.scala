import java.util.Properties
import java.util.regex.{Pattern, Matcher}
import play.exceptions.ConfigurationException
import play.jobs._
import play.libs.IO
import play.Play
import util.YamlLoader

@OnApplicationStart class BootStrap extends Job {

  override def doJob {

    import models._
    import play.test._
    import scala.collection.JavaConversions._

    // also retrieve user-based properties when running in test mode
    if (Play.id == "test") {
      val user = Option(System.getProperty("user")).getOrElse(throw new ConfigurationException("When running in test mode, specify the user using the JVM arg '-Duser=melvin'"))

      val pattern: Pattern = Pattern.compile("^%([a-zA-Z0-9_\\-]+)\\.(.*)$")
      val config: Properties = IO.readUtf8Properties(Play.conf.inputstream())
      for (key <- config.keySet()) {
        val matcher: Matcher = pattern.matcher(key + "")
        if (matcher.matches) {
          val instance = matcher.group(1)
          if (instance == user) {
            Play.configuration.put(matcher.group(2), config.get(key).toString.trim)
          }
        }
      }
    }

    // Check if all required launch properties are there
    Yaml[List[String]]("launchProperties.yml") map {
      key =>
        val value: String = Play.configuration.getProperty(key)
        if (value == null || value.trim().length() == 0) {
          throw new ConfigurationException("Required configuration property '" + key + "' is not configured. If you are running in dev mode, make sure you started the framework with 'play run --%youruser'")
        }
    }

    // Import initial data if the database is empty
    if (User.count() == 0) {
      YamlLoader.load[List[Any]]("initial-data.yml").foreach {
        _ match {
          case u: User => User.insert(u.copy(password = play.libs.Crypto.passwordHash(u.password)))
          case _ =>
        }
      }
    }
  }

}