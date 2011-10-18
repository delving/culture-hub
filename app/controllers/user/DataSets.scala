package controllers.user

import play.mvc.results.Result
import extensions.CHJson
import scala.collection.JavaConversions._
import com.mongodb.BasicDBObject
import models._
import models.DataSetState._
import controllers.{ShortDataSet, DelvingController}
import java.util.Date


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSets extends DelvingController with UserSecured {

  // TODO check rights for the accessed dataset
  def dataSet(spec: String): Result = Template('spec -> Option(spec), 'factDefinitions -> asJavaList(DataSet.factDefinitionList.filterNot(factDef => factDef.automatic)), 'recordDefinitions -> RecordDefinition.recordDefinitions.map(rDef => rDef.prefix))

  // TODO check rights for the accessed dataset
  def dataSetSubmit(data: String): Result = {

    val dataSet = CHJson.parse[ShortDataSet](data)
    val spec: String = dataSet.spec
    val factsObject = new BasicDBObject(dataSet.facts)

    def buildMappings(recordDefinitions: List[String]): Map[String, Mapping] = {
      val mappings = recordDefinitions.map {
        recordDef => (recordDef, Mapping(format = RecordDefinition.recordDefinitions.filter(rDef => rDef.prefix == recordDef).head))
      }
      mappings.toMap[String, Mapping]
    }

    def updateMappings(recordDefinitions: List[String], mappings: Map[String, Mapping]): Map[String, Mapping] = {
      val existing = mappings.filter(m => recordDefinitions.contains(m._1))
      val added = recordDefinitions.filter(prefix => !mappings.keys.contains(prefix))
      existing ++ buildMappings(added)
    }

    // TODO handle all "automatic facts"
    factsObject.append("spec", spec)

    dataSet.id match {
      // TODO for update, add the operator that appends key-value pairs rather than setting all
      case Some(id) => {
        val existing = DataSet.findOneByID(id).get
        val updatedDetails = existing.details.copy(facts = factsObject)
        val updated = existing.copy(spec = spec, details = updatedDetails, mappings = updateMappings(dataSet.recordDefinitions, existing.mappings) )
        DataSet.save(updated)
        }
      case None => DataSet.insert(
        DataSet(
          spec = dataSet.spec,
          node = getNode,
          user_id = connectedUserId,
          state = DataSetState.INCOMPLETE,
          lastUploaded = new Date(),
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
    Json(dataSet)
  }

  def index(spec: String): Result = {
    val dataSet = DataSet.findBySpec(spec).getOrElse(return NotFound("DataSet %s not found".format(spec)))

    // TODO
    // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

    dataSet.state match {
      case DISABLED | UPLOADED =>
        changeState(dataSet, DataSetState.QUEUED)
        Redirect("/%s/dataset".format(connectedUser))
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
        Redirect("/%s/dataset".format(connectedUser))
      case _ => Error("DataSet cannot be re-indexed in the current state")
    }

  }

  def cancel(spec: String): Result = {
    val dataSet = DataSet.findBySpec(spec).getOrElse(return NotFound("DataSet %s not found".format(spec)))
    dataSet.state match {
      case QUEUED | INDEXING =>
        changeState(dataSet, DataSetState.UPLOADED)
        DataSet.deleteFromSolr(dataSet)
        Redirect("/%s/dataset".format(connectedUser))
      case _ => Error("DataSet cannot be cancelled in the current state")
    }
  }

  def state(spec: String): Result = {
    Json(Map("state" -> DataSet.getStateBySpec(spec).name))
  }

  def indexingStatus(spec: String): Result = {
    val state = DataSet.getIndexingState(spec) match {
      case (a, b) if a == b => "DONE"
      case (a, b) => ((a.toDouble / b) * 100).round
    }
    Json(Map("status" -> state))
  }

  def disable(spec: String): Result = {
    val dataSet = DataSet.findBySpec(spec).getOrElse(return NotFound("DataSet %s not found".format(spec)))

    // TODO
    // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

    dataSet.state match {
      case QUEUED | INDEXING | ERROR | ENABLED =>
        val updatedDataSet = changeState(dataSet, DataSetState.DISABLED)
        DataSet.deleteFromSolr(updatedDataSet)
        Redirect("/%s/dataset".format(connectedUser))
      case _ => Error("DataSet cannot be disabled in the current state")
    }
  }

  def enable(spec: String): Result = {
    val dataSet = DataSet.findBySpec(spec).getOrElse(return NotFound("DataSet %s not found".format(spec)))

    // TODO
    // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

    dataSet.state match {
      case DISABLED =>
        changeState(dataSet, DataSetState.ENABLED)
        Redirect("/%s/dataset".format(connectedUser))
      case _ => Error("DataSet cannot be enabled in the current state")
    }
  }

  def delete(spec: String): Result = {
    val dataSet = DataSet.findBySpec(spec).getOrElse(return NotFound("DataSet %s not found".format(spec)))

    // TODO
    // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

    dataSet.state match {
      case INCOMPLETE | DISABLED | ERROR | UPLOADED =>
        DataSet.delete(dataSet)
        Redirect("/%s/dataset".format(connectedUser))
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

