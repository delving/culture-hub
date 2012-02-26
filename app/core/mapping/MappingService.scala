package core.mapping

import eu.delving.metadata.{MetadataModel, MetadataModelImpl}
import models.{RecordDefinition, DataSet}
import play.api.Logger

/**
 * Initializes the MetadataModel used by the mapping engine
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object MappingService {

  val metadataModel: MetadataModel = new MetadataModelImpl

  def init() {
    try {
      Logger("CultureHub").info("Initializing MappingService")
      val metadataModelImpl = metadataModel.asInstanceOf[MetadataModelImpl]
      metadataModelImpl.setFactDefinitionsFile(DataSet.getFactDefinitionFile)
      val recordDefinitions = RecordDefinition.getRecordDefinitionFiles
      recordDefinitions.foreach {
        definition => Logger("CultureHub").info("Loading record definition: " + definition.getAbsolutePath)
      }
      metadataModelImpl.setRecordDefinitionFiles(recordDefinitions : _*)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }

  }


}