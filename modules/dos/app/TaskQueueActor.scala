import _root_.util.Logging
import akka.actor.Actor
import models.dos.{TaskType, TaskState, Task}
import play.api.Logger
import processors._

/**
 * Scheduled actor that processes queued tasks, one at a time
 *
 * TODO turn into round-robin router, and execute tasks in parallel
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class TaskQueueActor extends Actor with Logging {

  def receive = {

    case Poll =>
      Task.all.foreach {
        taskDAO =>
          val head = taskDAO.list(TaskState.QUEUED).headOption
          head foreach {
            task =>
              taskDAO.start(task)
              try {
                task.taskType match {
                  case TaskType.THUMBNAILS_CREATE => GMThumbnailCreationProcessor.process(task, Map("sizes" -> controllers.dos.thumbnailSizes.values.toList))
                  case TaskType.THUMBNAILS_DELETE => ThumbnailDeletionProcessor.process(task)
                  case TaskType.FLATTEN => TIFFlatteningProcessor.process(task)
                  case TaskType.TILES => PTIFTilingProcessor.process(task)
                }
              } catch {
                case t =>
                  t.printStackTrace()
                  error(task, "Error running task of kind '%s' on path '%s': %s".format(task.taskType.name, task.path, t.getMessage))
              } finally {
                taskDAO.finish(task)
              }
          }
      }

    case a@_ => Logger("DOS").error("huh? ==> " + a)
  }

}

case object Poll


