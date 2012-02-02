package core.mapping

import eu.delving.metadata.{MetadataModel, MetadataModelImpl}
import models.{RecordDefinition, DataSet}


/**
 * Initializes the MetadataModel used by the mapping engine
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object MappingService {

  val metadataModel: MetadataModel = new MetadataModelImpl

  def init() {
    try {
      val metadataModelImpl = metadataModel.asInstanceOf[MetadataModelImpl]
      metadataModelImpl.setFactDefinitionsFile(DataSet.getFactDefinitionFile)
      metadataModelImpl.setRecordDefinitionFiles(RecordDefinition.getRecordDefinitionFiles: _*)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }

  }


}