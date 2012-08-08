package core.mapping

import models.RecordDefinition
import play.api.{Play, Logger}
import play.api.Play.current
import eu.delving.MappingEngine
import eu.delving.metadata._
import scala.collection.JavaConverters._
import org.w3c.dom.Node
import eu.delving.groovy.XmlSerializer

/**
 * Initializes the MetadataModel used by the mapping engine
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object MappingService {

  var recDefModel: RecDefModel = null
  val serializer = new XmlSerializer

  def init() {
    try {
      Logger("CultureHub").info("Initializing MappingService")

      val recordDefinitions = RecordDefinition.getRecordDefinitionResources(None)
      recordDefinitions.foreach {
        definition => Logger("CultureHub").info("Loading record definition: " + definition.getPath)
      }

      recDefModel = new RecDefModel {
        def createRecDef(prefix: String): RecDefTree = {
          RecDefTree.create(
            RecDef.read(Play.classloader.getResourceAsStream("definitions/%s/%s-record-definition.xml".format(prefix, prefix)))
          )
        }
      }

    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }

  }

  def nodeTreeToXmlString(node: Node): String = {
    val serialized = serializer.toXml(node)
    // chop of the XML prefix. kindof a hack. this should be a regex instead, more robust
    val xmlPrefix = """<?xml version='1.0' encoding='UTF-8'?>"""
    if(serialized.startsWith(xmlPrefix)) {
      serialized.substring(xmlPrefix.length)
    } else {
      serialized
    }
  }

}