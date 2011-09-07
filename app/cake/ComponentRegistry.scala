package cake

import extensions.{ObjectIdTypeBinder, ScalaListTypeBinder}
import util.{ThemeHandler, ThemeHandlerComponent}

/**
 * This object uses the Cake pattern to manage Dependency Injection, see also
 * http://jonasboner.com/2008/10/06/real-world-scala-dependency-injection-di.html
 *
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/6/11 4:36 PM  
 */

trait MetadataModelComponent {

  import eu.delving.metadata.MetadataModel

  val metadataModel: MetadataModel
}

trait MetaRepoComponent {

  import eu.delving.sip.AccessKey

  val accessKey: AccessKey
  //  val metaRepo: MetaRepo
}


// =======================
// instantiate the services in a module
object ComponentRegistry extends MetadataModelComponent with ThemeHandlerComponent with MetaRepoComponent {

  import scala.collection.JavaConversions._
  import play.Play
  import eu.delving.sip.AccessKey
  import eu.delving.metadata.MetadataModel
  import eu.delving.metadata.MetadataModelImpl

  val metadataModel: MetadataModel = new MetadataModelImpl
  metadataModel.asInstanceOf[MetadataModelImpl].setDefaultPrefix(Play.configuration.getProperty("services.harvindexing.prefix"))
  metadataModel.asInstanceOf[MetadataModelImpl].setRecordDefinitionResources(List("/abm-record-definition.xml", "/icn-record-definition.xml", "/ese-record-definition.xml"))

  val accessKey = new AccessKey
  accessKey.setServicesPassword(Play.configuration.getProperty("services.password").trim)

  //  val metaRepo = new MetaRepoImpl
  //  metaRepo.setResponseListSize(Play.configuration.getProperty("services.pmh.responseListSize").trim)
  //  metaRepo.setHarvestStepSecondsToLive(180)

  // FIXME this way of initializing things does not allow for a very good exception handling. if something goes wrong in startup we get an ClassNotDefFound in clients of the ComponentRegistry
  val themeHandler: ThemeHandler = new ThemeHandler
  try {
    themeHandler.startup()
  } catch {
    case t: Throwable => t.printStackTrace()
  }

  play.data.binding.Binder.register(classOf[scala.collection.immutable.List[String]], new ScalaListTypeBinder)
  play.data.binding.Binder.register(classOf[org.bson.types.ObjectId], new ObjectIdTypeBinder)
}