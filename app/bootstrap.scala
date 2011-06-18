import play.jobs._
    
@OnApplicationStart class BootStrap extends Job {

    override def doJob {

        import models._
        import play.test._

        // Import initial data if the database is empty
        if(User.count().single() == 0) {
            Yaml[List[Any]]("initial-data.yml").foreach {
                _ match {
                    case u:User => User.create(u)
                }
            }
        }

    }

}