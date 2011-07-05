import com.novus.salat.Context
import play.jobs._
import play.Play

@OnApplicationStart class BootStrap extends Job {

  override def doJob {

    import models._
    import play.test._


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