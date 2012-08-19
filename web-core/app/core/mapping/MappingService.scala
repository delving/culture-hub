package core.mapping

import core.schema.SchemaProvider
import models.RecordDefinition
import play.api.Logger
import play.api.Play.current
import eu.delving.metadata._
import scala.collection.JavaConverters._
import org.w3c.dom.Node
import eu.delving.groovy.XmlSerializer
import eu.delving.schema.SchemaType
import java.io.ByteArrayInputStream

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

      recDefModel = new RecDefModel {
        def createRecDef(prefix: String): RecDefTree = {
          // FIXME need to pass version when it will be available in the RecDefModel
          val schema = SchemaProvider.getSchema(prefix, "1.0.0", SchemaType.RECORD_DEFINITION)
          if(schema.isEmpty) {
            throw new RuntimeException("Empty schema for prefix " + prefix)
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
    if(serialized.startsWith(xmlPrefix)) {
      serialized.substring(xmlPrefix.length)
    } else {
      serialized
    }
  }

}