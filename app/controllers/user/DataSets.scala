package controllers.user

import play.mvc.results.Result
import extensions.CHJson
import scala.collection.JavaConversions._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.{BasicDBObject, WriteConcern}
import controllers.{ShortDataSet, DelvingController}
import org.scala_tools.time.Imports._
import models._
import models.DataSetState._

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

    val dataSet = CHJson.parse[ShortDataSet](data)
    val spec: String = dataSet.spec
    val factsObject = new BasicDBObject(dataSet.facts)

    def buildMappings(recordDefinitions: List[String]): Map[String, Mapping] = {
      (for (recordDef <- recordDefinitions) yield {
        (recordDef, Mapping(format = RecordDefinition.recordDefinitions.filter(rDef => rDef.prefix == recordDef).head))
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
          user_id = connectedUserId,
          state = DataSetState.INCOMPLETE,
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

  def index(spec: String): Result = {
    val dataSet = DataSet.findBySpec(spec).getOrElse(return NotFound("DataSet %s not found".format(spec)))

    // TODO
    // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

    dataSet.state match {
      case DISABLED | UPLOADED =>
        changeState(dataSet, DataSetState.QUEUED)
        Ok
      case _ => Error("DataSet cannot be indexed in the current state")
    }
  }

  def reIndex(spec: String): Result = {
    val dataSet = DataSet.findBySpec(spec).getOrElse(return NotFound("DataSet %s not found".format(spec)))

    // TODO
    // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

    dataSet.state match {
      case ENABLED =>
        changeState(dataSet, DataSetState.QUEUED)
        Ok
      case _ => Error("DataSet cannot be re-indexed in the current state")
    }

  }

  def disable(spec: String): Result = {
    val dataSet = DataSet.findBySpec(spec).getOrElse(return NotFound("DataSet %s not found".format(spec)))

    // TODO
    // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

    dataSet.state match {
      case QUEUED | INDEXING | ERROR | ENABLED =>
        val updatedDataSet = changeState(dataSet, DataSetState.DISABLED)
        DataSet.deleteFromSolr(updatedDataSet)
        Ok
      case _ => Error("DataSet cannot be disabled in the current state")
    }
  }

  def delete(spec: String): Result = {
    val dataSet = DataSet.findBySpec(spec).getOrElse(return NotFound("DataSet %s not found".format(spec)))

    // TODO
    // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

    dataSet.state match {
      case INCOMPLETE | DISABLED | ERROR | UPLOADED =>
        DataSet.delete(dataSet)
        Ok
      case _ => Error("DataSet cannot be deleted in the current state")
    }
  }

  private def changeState(dataSet: DataSet, state: DataSetState): DataSet = {
    val mappings = dataSet.mappings.transform((key, map) => map.copy(rec_indexed = 0))
    val updatedDataSet = dataSet.copy(state = state, mappings = mappings)
    DataSet.save(updatedDataSet)
    updatedDataSet
  }

}

