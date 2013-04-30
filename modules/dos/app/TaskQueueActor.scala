import util.{ OrganizationConfigurationHandler, Logging }
import akka.actor.{ Cancellable, Actor }
import models.dos.{ TaskType, TaskState, Task }
import models.OrganizationConfiguration
import play.api.libs.concurrent.Akka
import play.api.Logger
import processors._
import scala.concurrent.duration._
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Scheduled actor that processes queued tasks, one at a time
 *
 * TODO turn into round-robin router, and execute tasks in parallel
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class TaskQueueActor extends Actor with Logging {

  private var scheduler: Cancellable = null

  override def preStart() {
    scheduler = Akka.system.scheduler.schedule(
      0 seconds,
      10 seconds,
      self,
      Poll
    )
  }

  override def postStop() {
    scheduler.cancel()
  }

  def receive = {

    case Poll =>
      Task.all.foreach {
        taskDAO =>
          val head = taskDAO.list(TaskState.QUEUED).headOption
          head foreach {
            task =>
              taskDAO.start(task)
              try {
                implicit val configuration = OrganizationConfigurationHandler.getByOrgId(task.orgId)
                task.taskType match {
                  case TaskType.THUMBNAILS_CREATE => GMThumbnailCreationProcessor.process(task, Map("sizes" -> controllers.dos.thumbnailSizes.values.toList))
                  case TaskType.THUMBNAILS_DELETE => ThumbnailDeletionProcessor.process(task)
                  case TaskType.NORMALIZE => TIFFNormalizationProcessor.process(task)
                  case TaskType.TILES => PTIFTilingProcessor.process(task)
                }
              } catch {
                case t: Throwable =>
                  t.printStackTrace()
                  error(task, "Error running task of kind '%s' on path '%s': %s".format(task.taskType.name, task.path, t.getMessage))
              } finally {
                taskDAO.finish(task)
              }
          }
      }

    case a @ _ => Logger("DOS").error("huh? ==> " + a)
  }

}

case object Poll

