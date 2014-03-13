package actors

import akka.actor.Actor
import exceptions.StorageInsertionException
import play.api.libs.Files.TemporaryFile
import models.{ OrganizationConfiguration, DataSetState, DataSet }
import play.api.Logger
import controllers.ErrorReporter
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import java.io.InputStream
import core.HubServices
import util.SIPDataParser
import models.statistics.DataSetStatistics

class ReceiveSource extends Actor {

  var tempFileRef: TemporaryFile = null

  def receive = {
    case SourceStream(dataSet, userName, inputStream, tempFile, conf) =>
      implicit val configuration = conf
      val now = System.currentTimeMillis()

      // explicitly reference the TemporaryFile so it can't get garbage collected as long as this actor is around
      tempFileRef = tempFile

      try {
        receiveSource(dataSet, userName, inputStream) match {
          case Left(t) =>
            DataSet.dao.invalidateHashes(dataSet)
            val message = if (t.isInstanceOf[StorageInsertionException]) {
              Some("""Error while inserting record:
                     |
                     |%s
                     |
                     |Cause:
                     |
                     |%s
                     | """.stripMargin.format(t.getMessage, t.getCause.getMessage)
              )
            } else {
              Some(t.getMessage)
            }
            DataSet.dao.updateState(dataSet, DataSetState.ERROR, Some(userName), message)
            Logger("CultureHub").error(
              "Error while parsing records for spec %s of org %s".format(
                dataSet.spec, dataSet.orgId
              ),
              t
            )
            ErrorReporter.reportError(
              "DataSet Source Parser", t,
              "Error occured while parsing records for spec %s of org %s".format(
                dataSet.spec, dataSet.orgId
              )
            )

          case Right(inserted) =>
            val duration = Duration(System.currentTimeMillis() - now, TimeUnit.MILLISECONDS)
            Logger("CultureHub").info(
              "Finished parsing source for DataSet %s of organization %s. %s records inserted in %s seconds."
                .format(
                  dataSet.spec, dataSet.orgId, inserted, duration.toSeconds
                )
            )
        }

      } catch {
        case t: Throwable =>
          Logger("CultureHub").error(
            "Exception while processing uploaded source %s for DataSet %s".format(
              tempFile.file.getAbsolutePath, dataSet.spec
            ),
            t
          )
          DataSet.dao.invalidateHashes(dataSet)
          DataSet.dao.updateState(
            dataSet, DataSetState.ERROR, Some(userName),
            Some("Error while parsing uploaded source: " + t.getMessage)
          )

      } finally {
        tempFileRef = null
      }
  }

  private def receiveSource(dataSet: DataSet, userName: String, inputStream: InputStream)(implicit configuration: OrganizationConfiguration): Either[Throwable, Long] = {

    try {
      val uploadedRecords = SourceHelper.loadSourceData(dataSet, inputStream) // todo: loadSourceData should be here
      DataSet.dao.updateRecordCount(dataSet, uploadedRecords)
      DataSet.dao.updateState(dataSet, DataSetState.UPLOADED, Some(userName))
      Right(uploadedRecords)
    } catch {
      case t: Exception => return Left(t)
    }
  }

}

case class SourceStream(
  dataSet: DataSet,
  userName: String,
  stream: InputStream,
  temporaryFile: TemporaryFile,
  configuration: OrganizationConfiguration)

object SourceHelper {

  private def basexStorage(implicit configuration: OrganizationConfiguration) = HubServices.basexStorages.getResource(configuration)

  def loadSourceData(dataSet: DataSet, source: InputStream)(implicit configuration: OrganizationConfiguration): Long = {

    val prefix: Option[String] = None

    // until we have a better concept on how to deal with per-collection versions, do not make use of them here, but drop the data instead
    val mayCollection = basexStorage.openCollection(dataSet, prefix)
    val collection = if (mayCollection.isDefined) {
      basexStorage.deleteCollection(mayCollection.get, prefix)
      basexStorage.createCollection(dataSet, prefix)
    } else {
      basexStorage.createCollection(dataSet, prefix)
    }

    val parser = new SIPDataParser(source, dataSet, "delving-sip-source", "input")

    // use the uploaded statistics to know how many records we expect. For that purpose, use the mappings to know what prefixes we have...
    // TODO we should have a more direct route to know what to expect here.
    val totalRecords = dataSet.mappings.keySet.headOption.flatMap {
      schema => DataSetStatistics.dao.getMostRecent(dataSet.orgId, dataSet.spec, schema).map(_.recordCount)
    }
    val modulo = if (totalRecords.isDefined) math.round(totalRecords.get / 100) else 100

    def onRecordInserted(count: Long) {
      if (count % (if (modulo == 0) 100 else modulo) == 0) DataSet.dao.updateRecordCount(dataSet, count)
    }

    basexStorage.store(collection, prefix, parser, parser.namespaces, onRecordInserted)
  }
}
