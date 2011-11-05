package controllers

import models.DataSet
import play.mvc.results.Result
import scala.collection.JavaConversions.asJavaList


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends DelvingController {

  def list(user: Option[String], page: Int = 1): Result = {
    // TODO visibility (public, private)
    val dataSetsPage = DataSet.findAllByOwner(connectedUserId).page(page)
    val items: List[ShortDataSet] = dataSetsPage._1 
    Template('title -> listPageTitle("dataset"), 'items -> items, 'page -> page, 'count -> dataSetsPage._2)
  }

  def dataSet(spec: String): Result = {
   // TODO check if connected user has access
    val dataSet = DataSet.findBySpec(spec)

    request.format match {
      case "html" => {
        val ds = dataSet.getOrElse(return NotFound(&("datasets.dataSetNotFound", spec)))
        val describedFacts = DataSet.factDefinitionList.map(factDef => Fact(factDef.name, factDef.prompt, Option(ds.details.facts.get(factDef.name)).getOrElse("").toString))
        Template('dataSet -> ds, 'facts -> asJavaList(describedFacts))
      }
      case "json" => if(dataSet == None) Json(ShortDataSet(userName = connectedUser)) else {
        val dS = dataSet.get
        Json(ShortDataSet(id = Some(dS._id), spec = dS.spec, facts = dS.getFacts, userName = dS.getUser.userName, recordDefinitions = dS.recordDefinitions))
      }
    }

  }

}