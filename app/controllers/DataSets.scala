package controllers

import play.templates.Html
import models.DataSet
import com.mongodb.casbah.Implicits._
import java.io.File
import play.exceptions.ConfigurationException
import xml.{Node, XML}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends DelvingController {

  lazy val factDefinitionList = parseFactDefinitionList

  import views.Dataset._

  def list: Html = {
    // TODO visibility (public, private)
    val dataSets = DataSet.findAllByOwner(connectedUserId)
    html.list(dataSets)
  }

  def view(spec: String): Html = {
   // TODO check if connected user has access
    val dataSet = DataSet.findBySpec(spec)
    html.view(dataSet)
  }

  def facts(spec: String): AnyRef = {
    // TODO check if connected user has access
    val dataSet = DataSet.findBySpec(spec)
    val initialFacts = (factDefinitionList.map(factDef => (factDef.name, ""))).toMap[String, AnyRef]
    val storedFacts = (for (fact <- dataSet.details.facts) yield (fact._1, fact._2)).toMap[String, AnyRef]
    val facts = initialFacts ++ storedFacts

    val describedFacts = for(factDef <- factDefinitionList) yield Fact(factDef.name, factDef.prompt, facts(factDef.name).toString)

    request.format match {
      case "html" => html.facts(dataSet, describedFacts)
      case "json" => Json(facts)
      case _ => BadRequest
    }
  }

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
      for (option <- (node \ "options" \ "string")) yield (option text)
    )
  }

}

case class FactDefinition(name: String, prompt: String, tooltip: String, automatic: Boolean = false, options: Seq[String]) {
  def hasOptions = !options.isEmpty
}

case class Fact(name: String, prompt: String, value: String)
