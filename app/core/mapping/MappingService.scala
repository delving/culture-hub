package core.mapping

import models.{RecordDefinition, DataSet}
import play.api.{Play, Logger}
import play.api.Play.current
import eu.delving.sip.{IndexDocument, MappingEngine}
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
    val indexDocument: IndexDocument = engine.toIndexDocument(rawRecord)
    indexDocumentToXmlString(indexDocument)
  }

  def indexDocumentToXmlString(indexDocument: IndexDocument): String = {
    indexDocument.getMap.asScala.foldLeft("") {
      (output: String, e: (String, java.util.List[IndexDocument#Value])) => {
        val unMungedKey = stripDynamicFieldLabels(e._1.toString.replaceFirst("_", ":"))
        output + {
          val list: List[IndexDocument#Value] = e._2.asScala.toList
          list.map(v => "<%s>%s</%s>".format(unMungedKey, v.toString, unMungedKey)).mkString("", "\n", "\n")
        }
      }
    }
  }

  def nodeTreeToXmlString(node: Node): String = XmlSerializer.toXml(node)

  // duplicated here so as to avoid a dependency on the SolrBindingService
  private def stripDynamicFieldLabels(fieldName: String): String = {
    fieldName.replaceFirst("_(string|facet|location|int|single|text|date|link|s|lowercase)$", "").replaceFirst("^(facet|sort|sort_all)_", "")
  }

}