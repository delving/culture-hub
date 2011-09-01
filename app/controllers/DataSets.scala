package controllers

import play.templates.Html
import models.DataSet
import org.bson.types.ObjectId

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends DelvingController {

  import views.Dataset._

  def list: Html = {
    // TODO visibility (public, private)
    val dataSets = DataSet.findAllByOwner(connectedUserId)
    html.list(dataSets)
  }

  def dataSet(spec: String): AnyRef = {
   // TODO check if connected user has access
    val dataSet = DataSet.findBySpec(spec)

    request.format match {
      case "html" => {
        val ds = dataSet.getOrElse(return NotFound("DataSet '%s' was not found".format(spec)))
        val describedFacts = for(factDef <- DataSet.factDefinitionList) yield Fact(factDef.name, factDef.prompt, Option(ds.details.facts.get(factDef.name)).getOrElse("").toString)
        html.view(ds, describedFacts)
      }
      case "json" => if(dataSet == None) Json(DataSetModel.empty) else {
        val dS = dataSet.get
        Json(DataSetModel(id = Some(dS._id), spec = dS.spec, facts = dS.getFacts))
      }
    }

  }

}

case class DataSetModel(id: Option[ObjectId] = None, spec: String, facts: Map[String, String] = Map.empty[String, String], recordDefinitions: List[String] = List.empty[String])
object DataSetModel {
  val empty = DataSetModel(spec = "")
}

case class Fact(name: String, prompt: String, value: String)
