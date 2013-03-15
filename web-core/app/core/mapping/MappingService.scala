package core.mapping

import core.SchemaService
import eu.delving.metadata._
import org.w3c.dom.Node
import eu.delving.groovy.XmlSerializer
import eu.delving.schema.{ SchemaVersion, SchemaType }
import java.io.ByteArrayInputStream

/**
 * Initializes the MetadataModel used by the mapping engine
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object MappingService {

  private val serializer = new XmlSerializer

  def recDefModel(schemaService: SchemaService): RecDefModel = {
    try {
      new RecDefModel {

        def createRecDefTree(schemaVersion: SchemaVersion): RecDefTree = {
          val schema = schemaService.getSchema(schemaVersion.getPrefix, schemaVersion.getVersion, SchemaType.RECORD_DEFINITION)
          if (schema.isEmpty) {
            throw new RuntimeException("Empty schema for prefix %s and version %s".format(schemaVersion.getPrefix, schemaVersion.getVersion))
          } else {
            RecDefTree.create(
              RecDef.read(new ByteArrayInputStream(schema.get.getBytes("utf-8")))
            )
          }
        }
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }
  }

  def nodeTreeToXmlString(node: Node, fromMapping: Boolean): String = {
    val serialized = serializer.toXml(node, fromMapping)
    // chop of the XML prefix. kindof a hack. this should be a regex instead, more robust
    val xmlPrefix = """<?xml version='1.0' encoding='UTF-8'?>"""
    if (serialized.startsWith(xmlPrefix)) {
      serialized.substring(xmlPrefix.length)
    } else {
      serialized
    }
  }

}