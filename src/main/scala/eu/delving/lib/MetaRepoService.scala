package eu.delving.lib

import net.liftweb.http.rest.RestHelper
import net.liftweb.common._
import eu.delving.model.{DataSetState, DataSet, User}
import net.liftweb.sitemap.Menu
import net.liftweb.sitemap.Loc.TestAccess
import net.liftweb.http._

/**
 * Dispatch the services
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */


object MetaRepoService extends RestHelper {

  val log = Logger("MetaRepoService")

  val sitemap = List(
    Menu("Service") / "service",
    Menu("Protected Service") / "protected-service" >> TestAccess(() => if (User.notLoggedIn_?) Full(ForbiddenResponse("No way!")) else Empty)
  )

  serve {

    case "dataset" :: Nil XmlGet _ => {
      <datasets>
        { for (dataSet <- DataSet.findAll) yield
        <dataset>
          <spec>{ dataSet.spec }</spec>
        </dataset>
        }
      </datasets>
    }

    case "dataset" :: spec :: command :: Nil XmlPost _ => {
      DataSet.find("spec", spec) match {
        case Full(dataSet) => {
          command match {
            case "INDEX" => <index/>
            case "REINDEX" => <reindex/>
            case "DISABLE" => <disable/>
            case "DELETE" => <delete/>
            case _ => <command-not-found/>
          }
        }
        case _ => NotFoundResponse("Dataset "+spec)
      }
    }

    case "dataset" :: "submit" :: spec :: fileType :: fileName :: Nil XmlPost _ => {
//      DataSet.find("spec", spec).openOr(NotFoundResponse("dataset " + spec)).map(ds => )
      DataSet.find("spec", spec) match {
        case Full(dataSet) => {
          fileType match {
            case "FACTS" => <facts/>
            case "SOURCE" => <source/>
            case "MAPPING" => <mapping/>
            case _ => <file-type-not-found/>
          }
        }
        case _ => NotFoundResponse("Dataset "+spec)
      }
    }


    case "service" :: Nil XmlGet _ => fakeService("open")

    case "protected-service" :: Nil XmlGet _ if User.loggedIn_? => fakeService("you're in")

  }

  def createOne() = {
    DataSet.createRecord.spec("fresh").state(DataSetState.Incomplete).sourceHash("hash").save
    OkResponse
  }

  def fakeService(say : String) = XmlResponse(
    <service>
      <ladies-and-gentlemen loggedIn={User.loggedIn_?.toString}>
         <the-delving-dispatcher status={say}/>
      </ladies-and-gentlemen>
    </service>
  )

}