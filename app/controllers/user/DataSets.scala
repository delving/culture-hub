package controllers.user

import play.mvc.results.Result
import extensions.CHJson
import scala.collection.JavaConversions._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.{BasicDBObject, WriteConcern}
import controllers.{DataSetModel, DelvingController}
import org.scala_tools.time.Imports._
import eu.delving.sip.DataSetState
import models._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends DelvingController with UserSecured {

  import views.User.Dataset._

  // TODO check rights for the accessed dataset
  def dataSetUpdate(spec: String): AnyRef = html.dataset(Option(spec), DataSet.factDefinitionList.filterNot(factDef => factDef.automatic), RecordDefinition.recordDefinitions.map(rDef => rDef.prefix))

  // TODO check rights for the accessed dataset
  def dataSetSubmit(data: String): Result = {

    val dataSet = CHJson.parse[DataSetModel](data)
    val spec: String = dataSet.spec
    val factsObject = new BasicDBObject(dataSet.facts)

    def buildMappings(recordDefinitions: List[String]): Map[String, Mapping] = {
      (for(recordDef <- recordDefinitions) yield {
        (recordDef, Mapping(recordMapping = "", format = RecordDefinition.recordDefinitions.filter(rDef => rDef.prefix == recordDef).head))
      }).toMap[String, Mapping]
    }

    // TODO handle all "automatic facts"
    factsObject.append("spec", spec)

    dataSet.id match {
      // TODO for update, add the operator that appends key-value pairs rather than setting all
      case Some(id) => DataSet.update(MongoDBObject("spec" -> spec), MongoDBObject("$set" -> MongoDBObject("spec" -> spec, "details.facts" -> factsObject)), false, false, new WriteConcern())
      case None => DataSet.insert(
        DataSet(
          spec = dataSet.spec,
          node = getNode,
          user = connectedUserId,
          state = DataSetState.INCOMPLETE.toString,
          lastUploaded = DateTime.now,
          access = AccessRight(users = Map("foo" -> UserAction(user = UserReference("", "", "")))), // TODO
          details = Details(
            name = dataSet.facts("name").toString,
            facts = factsObject,
            metadataFormat = RecordDefinition("raw", "http://delving.eu/namespaces/raw", "http://delving.eu/namespaces/raw/schema.xsd")
          ),
          mappings = buildMappings(dataSet.recordDefinitions)
        )
      )
    }

    Ok
  }
}

