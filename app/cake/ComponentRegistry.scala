package cake

import extensions.{ObjectIdTypeBinder, ScalaListTypeBinder}
import util.{ThemeHandler, ThemeHandlerComponent}
import scala.collection.JavaConversions._
import play.Play
import eu.delving.sip.AccessKey
import eu.delving.metadata.MetadataModel
import eu.delving.metadata.MetadataModelImpl


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

  val metadataModel: MetadataModel = new MetadataModelImpl
  val accessKey = new AccessKey
  val themeHandler: ThemeHandler = new ThemeHandler

  //  val metaRepo = new MetaRepoImpl
  //  metaRepo.setResponseListSize(Play.configuration.getProperty("services.pmh.responseListSize").trim)
  //  metaRepo.setHarvestStepSecondsToLive(180)

  // TODO remove this when we have replaced the old code for theme handling
  play.data.binding.Binder.register(classOf[scala.collection.immutable.List[String]], new ScalaListTypeBinder)
  play.data.binding.Binder.register(classOf[org.bson.types.ObjectId], new ObjectIdTypeBinder)

  init()

  def init() {
    try {
      metadataModel.asInstanceOf[MetadataModelImpl].setDefaultPrefix(Play.configuration.getProperty("services.harvindexing.prefix"))
      metadataModel.asInstanceOf[MetadataModelImpl].setRecordDefinitionResources(List("/abm-record-definition.xml", "/icn-record-definition.xml", "/ese-record-definition.xml"))

      accessKey.setServicesPassword(Play.configuration.getProperty("services.password").trim)

      themeHandler.startup()
    } catch {
      case t: Throwable => t.printStackTrace()
    }

  }

}