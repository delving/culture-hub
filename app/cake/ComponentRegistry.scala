package cake

import util.{ThemeHandler, ThemeHandlerComponent}
import scala.collection.JavaConversions._
import eu.delving.metadata.MetadataModel
import eu.delving.metadata.MetadataModelImpl
import models.{DataSet, RecordDefinition}

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

  //  val metaRepo: MetaRepo
}


// =======================
// instantiate the services in a module
object ComponentRegistry extends MetadataModelComponent with ThemeHandlerComponent with MetaRepoComponent {

  val metadataModel: MetadataModel = new MetadataModelImpl
  val themeHandler: ThemeHandler = new ThemeHandler

  //  val metaRepo = new MetaRepoImpl
  //  metaRepo.setResponseListSize(Play.configuration.getProperty("services.pmh.responseListSize").trim)
  //  metaRepo.setHarvestStepSecondsToLive(180)

  init()

  def init() {
    try {
      val metadataModelImpl = metadataModel.asInstanceOf[MetadataModelImpl]
      metadataModelImpl.setFactDefinitionsFile(DataSet.getFactDefinitionFile)
      metadataModelImpl.setRecordDefinitionFiles(RecordDefinition.getRecordDefinitionFiles : _*)

      themeHandler.startup()
    } catch {
      case t: Throwable => t.printStackTrace()
    }

  }

}