import play.exceptions.ConfigurationException
import play.jobs._
import play.Play

@OnApplicationStart class BootStrap extends Job {

  override def doJob {

    import models._
    import play.test._

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
      Yaml[List[Any]]("initial-data.yml").foreach {
        _ match {
          case u: User => User.insert(u)
        }
      }
    }
  }

}