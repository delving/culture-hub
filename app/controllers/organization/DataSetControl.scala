package controllers.organization

import play.mvc.results.Result
import extensions.JJson
import scala.collection.JavaConversions._
import com.mongodb.BasicDBObject
import models._
import models.DataSetState._
import controllers.{ShortDataSet, DelvingController}
import java.util.Date
import components.Indexing


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object DataSetControl extends DelvingController with OrganizationSecured {

  // TODO check rights for the accessed dataset
  def dataSet(orgId: String, spec: String): Result = Template('spec -> Option(spec), 'factDefinitions -> asJavaList(DataSet.factDefinitionList.filterNot(factDef => factDef.automatic)), 'recordDefinitions -> RecordDefinition.recordDefinitions.map(rDef => rDef.prefix))

  // TODO check rights for the accessed dataset
  def dataSetSubmit(orgId: String, data: String): Result = {

    val dataSet = JJson.parse[ShortDataSet](data)
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
        val updated = existing.copy(spec = spec, details = updatedDetails, mappings = updateMappings(dataSet.recordDefinitions, existing.mappings), visibility = Visibility.get(dataSet.visibility))
        DataSet.save(updated)
        }
      case None => DataSet.insert(
        DataSet(
          spec = dataSet.spec,
          orgId = orgId,
          user_id = connectedUserId,
          state = DataSetState.INCOMPLETE,
          visibility = Visibility.get(dataSet.visibility),
          lastUploaded = new Date(),
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

  def index(orgId: String, spec: String): Result = {
    withDataSet(orgId, spec) { dataSet =>
      // TODO
      // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

      dataSet.state match {
        case DISABLED | UPLOADED | ERROR =>
          if(theme.metadataPrefix != None && dataSet.mappings.containsKey(theme.metadataPrefix.get)) {
            DataSet.addIndexingMapping(dataSet, theme.metadataPrefix.get)
            DataSet.changeState(dataSet, DataSetState.QUEUED)
          } else {
            // TODO give the user some decent feedback
            DataSet.changeState(dataSet, DataSetState.ERROR)
          }
          Redirect("/organizations/%s/dataset".format(orgId))
        case _ => Error(&("organization.datasets.cannotBeIndexed"))
      }
    }
  }

  def reIndex(orgId: String, spec: String): Result = {
    withDataSet(orgId, spec) { dataSet =>
      // TODO
      // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

      dataSet.state match {
        case ENABLED =>
          DataSet.addIndexingMapping(dataSet, theme.metadataPrefix.get)
          DataSet.changeState(dataSet, DataSetState.QUEUED)
          Redirect("/organizations/%s/dataset".format(orgId))
        case _ => Error(&("organization.datasets.cannotBeReIndexed"))
      }
    }
  }

  def cancel(orgId: String, spec: String): Result = {
    withDataSet(orgId: String, spec) { dataSet =>
      dataSet.state match {
        case QUEUED | INDEXING =>
          DataSet.changeState(dataSet, DataSetState.UPLOADED)
          try {
            Indexing.deleteFromSolr(dataSet)
          } catch {
            case _ => DataSet.changeState(dataSet, DataSetState.ERROR)
          }
          Redirect("/organizations/%s/dataset".format(orgId))
        case _ => Error(&("organization.datasets.cannotBeCancelled"))
      }
    }
  }

  def state(orgId: String, spec: String): Result = {
    Json(Map("state" -> DataSet.getStateBySpecAndOrgId(spec, orgId).name))
  }

  def indexingStatus(orgId: String, spec: String): Result = {
    val state = DataSet.getIndexingState(orgId, spec) match {
      case (a, b) if a == b => "DONE"
      case (a, b) => ((a.toDouble / b) * 100).round
    }
    Json(Map("status" -> state))
  }

  def disable(orgId: String, spec: String): Result = {
    withDataSet(orgId, spec) { dataSet =>

      // TODO
      // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

      dataSet.state match {
        case QUEUED | INDEXING | ERROR | ENABLED =>
          val updatedDataSet = DataSet.changeState(dataSet, DataSetState.DISABLED)
          Indexing.deleteFromSolr(updatedDataSet)
          Redirect("/organizations/%s/dataset".format(orgId))
        case _ => Error(&("organization.datasets.cannotBeDisabled"))
      }
    }
  }

  def enable(orgId: String, spec: String): Result = {
    withDataSet(orgId, spec) { dataSet =>

      // TODO
      // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

      dataSet.state match {
        case DISABLED =>
          DataSet.changeState(dataSet, DataSetState.ENABLED)
          Redirect("/organizations/%s/dataset".format(orgId))
        case _ => Error(&("organization.datasets.cannotBeEnabled"))
      }
    }
  }

  def delete(orgId: String, spec: String): Result = {
    withDataSet(orgId, spec) { dataSet =>

      // TODO
      // if(!DataSet.canUpdate(dataSet.spec, user)) { throw new UnauthorizedException(UNAUTHORIZED_UPDATE) }

      dataSet.state match {
        case INCOMPLETE | DISABLED | ERROR | UPLOADED =>
          DataSet.delete(dataSet)
          Redirect("/organizations/%s/dataset".format(orgId))
        case _ => Error(&("organization.datasets.cannotBeDeleted"))
      }
    }
  }

  def withDataSet(orgId: String, spec: String)(operation: DataSet => Result): Result = {
    val dataSet = DataSet.findBySpecAndOrgId(spec, orgId).getOrElse(return NotFound(&("organization.datasets.dataSetNotFound", spec)))
    operation(dataSet)
  }
}