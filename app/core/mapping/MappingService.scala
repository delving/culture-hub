package core.mapping

import models.{RecordDefinition, DataSet}
import play.api.{Play, Logger}
import play.api.Play.current
import eu.delving.sip.MappingEngine
import controllers.ModelImplicits
import eu.delving.metadata._
import java.io.FileInputStream
import scala.collection.JavaConverters._
import org.w3c.dom.Node
import eu.delving.groovy.XmlSerializer

/**
 * Initializes the MetadataModel used by the mapping engine
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object MappingService extends ModelImplicits {

  var recDefModel: RecDefModel = null

  def init() {
    try {
      Logger("CultureHub").info("Initializing MappingService")

      val recordDefinitions = RecordDefinition.getRecordDefinitionFiles
      recordDefinitions.foreach {
        definition => Logger("CultureHub").info("Loading record definition: " + definition.getAbsolutePath)
      }

      recDefModel = new RecDefModel {
        def createRecDef(prefix: String): RecDefTree = {
          RecDefTree.create(
            RecDef.read(new FileInputStream(
              recordDefinitions.find(f => f.getName.startsWith(prefix)).getOrElse(throw new RuntimeException("Cannot find Record Definition with prefix " + prefix))
            ))
          )
        }

        def getFactDefinitions: java.util.List[FactDefinition] = FactDefinition.read(DataSet.getFactDefinitionFile)

        def getPrefixes: java.util.Set[String] = recordDefinitions.map(_.getName.split("-").head).toSet.asJava
      }

    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }

  }

  def transformRecord(rawRecord: String, recordMapping: String, namespaces: Map[String, String]): String = {
    val engine: MappingEngine = new MappingEngine(recordMapping, Play.classloader, recDefModel, namespaces.asJava)
    val nodeTree = engine.toNode(rawRecord)
    nodeTreeToXmlString(nodeTree)
  }

  def nodeTreeToXmlString(node: Node): String = {
    val serialized = XmlSerializer.toXml(node)
    // chop of the XML prefix. kindof a hack
    if(serialized.startsWith("""<?xml version=\"1.0\" encoding=\"UTF-8\"?>""")) {
      serialized.substring("""<?xml version=\"1.0\" encoding=\"UTF-8\"?>""".length)
    } else {
      serialized
    }
  }

}