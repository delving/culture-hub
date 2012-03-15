package core.mapping

import eu.delving.metadata.{MetadataModel, MetadataModelImpl}
import models.{RecordDefinition, DataSet}
import play.api.{Play, Logger}
import play.api.Play.current
import eu.delving.sip.{IndexDocument, MappingEngine}
import controllers.ModelImplicits

/**
 * Initializes the MetadataModel used by the mapping engine
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object MappingService extends ModelImplicits {

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
  
  def transformXml(rawRecord: String, recordMapping: String, namespaces: Map[String, String]): Map[String, List[String]] = {
    import scala.collection.JavaConverters._
    val engine: MappingEngine = new MappingEngine(recordMapping, namespaces.asJava, Play.classloader, metadataModel)
    val indexDocument: IndexDocument = engine.executeMapping(rawRecord)
    indexDocument
  }


}