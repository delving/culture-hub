package controllers.organization

import play.api.mvc._
import controllers.{ SipCreatorEndPoint, OrganizationController }
import java.util.zip.ZipFile
import scala.collection.JavaConverters._
import scala.io.Source
import models._
import core.CultureHubPlugin
import com.mongodb.BasicDBObject
import models.FormatAccessControl
import core.messages.CollectionCreated
import models.Details
import com.mongodb.casbah.commons.MongoDBObject
import play.api.libs.ws.WS
import scala.concurrent.{ ExecutionContext, Await, Future }
import play.api.libs.Files.TemporaryFile
import org.apache.commons.io.FileUtils
import java.io.File
import ExecutionContext.Implicits.global
import play.api.libs.MimeTypes
import com.escalatesoft.subcut.inject.BindingModule

/**
 * Importer, useful for development and debugging, use e.g. like this::
 *
 *  curl -i -F name=RAEAD_delving.zip -F filedata=@RAEAD_delving.zip http://delving.localhost:9000/admin/dataset/import\?userName\=bob
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class DataSetImport(implicit val bindingModule: BindingModule) extends OrganizationController {

  def importSIP(userName: Option[String]) = Root {
    Action(parse.temporaryFile) {
      implicit request =>

        userName map { user =>

          val zipFile = new ZipFile(request.body.file)

          val allEntries = zipFile.entries.asScala
            .filterNot(_.isDirectory)
            .map { entry =>
              val name = entry.getName
              val is = zipFile.getInputStream(entry)
              (name, (is, entry.getTime))
            }.toMap

          val entries = allEntries.groupBy { e =>
            if (SipCreatorEndPoint.FileName.findAllMatchIn(e._1).isEmpty) {
              e._1
            } else {
              val SipCreatorEndPoint.FileName(dir, hash, kind, prefix, extension) = e._1
              kind
            }
          }.map { grouped =>
            val mostRecent = grouped._2.toSeq.sortBy(_._2._2).reverse.head
            (mostRecent._1, mostRecent._2._1)
          }.toMap

          log.info("Importer: found entries\n\n" + entries.map(_._1).mkString("\n"))

          entries.find(e => e._1.contains("dataset_facts.txt")).map { facts =>

            val s = Source.fromInputStream(facts._2, "UTF-8")
            val factsMap = s.getLines().map { line =>
              val Array(key, value) = line.split("=")
              (key, value)
            }.toMap

            // I can haz set?
            val spec = factsMap("spec")
            val set = DataSet.dao.findOne(MongoDBObject("spec" -> spec)) getOrElse {

              val formats = factsMap("schemaVersions").split(",").map { v =>
                val Array(prefix, version) = v.split("_")
                (prefix -> version)
              }.toMap

              DataSet.dao.insert(
                DataSet(
                  spec = spec,
                  orgId = configuration.orgId,
                  userName = user,
                  description = None,
                  state = DataSetState.INCOMPLETE,
                  details = Details(
                    name = factsMap("name"),
                    facts = new BasicDBObject(factsMap.asJava)
                  ),
                  invalidRecords = formats.map(f => (f._1, List.empty)),
                  mappings = formats.map(f => (f._1, Mapping(schemaPrefix = f._1, schemaVersion = f._2))),
                  formatAccessControl = formats.map(f => (f._1 -> FormatAccessControl(accessType = "public")))
                )
              )

              log.info("Created set for import " + spec)

              DataSetEvent ! DataSetEvent.Created(configuration.orgId, spec, connectedUser)
              CultureHubPlugin.broadcastMessage(CollectionCreated(spec, configuration))

            }

            // and now upload the stuff, hacky way
            val commands: Map[String, Future[String]] = entries
              .filterNot(f => SipCreatorEndPoint.FileName.findAllMatchIn(f._1).isEmpty)
              .filterNot(_._1.contains("_imported"))
              .map { file =>
                val cleanName = file._1.substring(file._1.indexOf("/") + 1)
                // why are there no stream utils around here to directly post from a stream?
                val t = new File(System.getProperty("java.io.tmpdir"), cleanName)
                t.createNewFile()
                val temp = TemporaryFile(t) // auto-cleanup on GC
                FileUtils.copyInputStreamToFile(file._2, t)
                val mimeType = {
                  val m = MimeTypes.forFileName(cleanName).getOrElse("unknown/unknown")
                  if (m == "application/x-compressed") "application/x-gzip" else m
                }
                log.info(s"Temporary extracted file at ${t.getAbsolutePath} with mimeType $mimeType")
                (
                  file._1,
                  WS.url(s"http://delving.localhost:9000/api/sip-creator/submit/${configuration.orgId}/$spec/$cleanName")
                  .withQueryString("userName" -> user)
                  .withHeaders(
                    "Content-Type" -> mimeType
                  )
                  .post(t).map(r => r.body))
              }.toMap

            import scala.concurrent.duration._
            val responses: Map[String, String] = commands.map(r => (r._1, Await.result(r._2, 10 seconds)))

            Ok(
              responses.map(r => s"${r._1} => ${r._2}").mkString("\n")
            )

          } getOrElse {
            BadRequest("No dataset_facts.txt found in this SIP")
          }
        } getOrElse {
          BadRequest("Nope.")
        }
    }

  }

}
