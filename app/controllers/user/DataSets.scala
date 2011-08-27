package controllers.user

import controllers.DelvingController
import java.io.File
import xml.{Node, XML}
import play.exceptions.ConfigurationException
import play.mvc.results.Result
import play.templates.Html

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends DelvingController {

  lazy val factDefinitionList = parseFactDefinitionList

  import views.User.Dataset._

  def dataSetUpdate(spec: String): Html = html.facts(spec, factDefinitionList)

  private def parseFactDefinitionList: Seq[FactDefinition] = {
    val file = new File("conf/fact-definition-list.xml")
    if (!file.exists()) throw new ConfigurationException("Fact definition configuration file not found!")
    val xml = XML.loadFile(file)
    for (e <- (xml \ "fact-definition")) yield parseFactDefinition(e)
  }

  private def parseFactDefinition(node: Node) = {
    FactDefinition(
      node \ "@name" text,
      node \ "prompt" text,
      node \ "toolTip" text,
      (node \ "automatic" text).equalsIgnoreCase("true"),
      for(option <- (node \ "options" \ "string")) yield (option text)
    )
  }

}

case class FactDefinition(name: String, prompt: String, tooltip: String, automatic: Boolean = false, options: Seq[String]) {
  def hasOptions = !options.isEmpty
}