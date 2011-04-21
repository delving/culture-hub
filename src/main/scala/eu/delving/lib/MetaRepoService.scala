package eu.delving.lib

import net.liftweb.http.rest.RestHelper
import _root_.net.liftweb.common._
import _root_.net.liftweb.http._
import eu.delving.model.{DataSetState, DataSet, User}

/**
 * Dispatch the services
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */


object MetaRepoService extends RestHelper {

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
        case _ => <dataset-not-found/>
      }
    }

    case "dataset" :: "submit" :: spec :: fileType :: fileName :: Nil XmlPost _ => {
      DataSet.find("spec", spec) match {
        case Full(dataSet) => {
          fileType match {
            case "FACTS" => <facts/>
            case "SOURCE" => <source/>
            case "MAPPING" => <mapping/>
            case _ => <file-type-not-found/>
          }
        }
        case _ => <dataset-not-found/>
      }
    }


    case "service" :: Nil XmlGet _ => service("open")
    case "protected-service" :: Nil XmlGet _ => if (User.notLoggedIn_?) Full(ForbiddenResponse("fuck off!")) else service("protected!")


// todo: figure out why this url is not protected by the SiteMap
//    case "protected-service" :: Nil XmlGet _ => service("protected!")
  }

  def createOne() = {
    DataSet.createRecord.spec("fresh").state(DataSetState.Incomplete).sourceHash("hash").save
    <ok/>
  }


  private def service(say : String) =
    <service>
      <ladies-and-gentlemen>
         <the-delving-dispatcher status={say}/>
      </ladies-and-gentlemen>
    </service>

}