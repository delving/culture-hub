package controllers.user

import controllers.DelvingController
import play.mvc.results.Result
import com.mongodb.casbah.commons.MongoDBObject
import java.io.File
import play.exceptions.ConfigurationException
import xml.{Node, XML}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends DelvingController {

  // done once, at object instantiation time
  parseFactDefinitionList

  import views.User.Dataset._

  def dataSetUpdate(spec: String): Result = Ok

  private def parseFactDefinitionList: Seq[FactDefinition] = {
    val file = new File("conf/fact-definition-list.xml")
    if (!file.exists()) throw new ConfigurationException("Fact definition configuration file not found!")
    val xml = XML.loadFile(file)

    val factDefinitionList = xml \ "fact-definition"

    for (e <- factDefinitionList) yield e match {
      case <fact-definition>{_*}</fact-definition> => parseFactDefinition(e)
    }
  }

  private def parseFactDefinition(node: Node) = {
    FactDefinition(
      node \ "@name" text,
      node \ "prompt" text,
      node \ "toolTip" text,
      (node \ "automatic" text) toBoolean,
      (for(option <- node \ "options") yield (option \ "string" text)).toList
    )
  }

}

case class FactDefinition(name: String, prompt: String, tooltip: String, automatic: Boolean = false, options: List[String]) {
  def hasOptions = !options.isEmpty
}