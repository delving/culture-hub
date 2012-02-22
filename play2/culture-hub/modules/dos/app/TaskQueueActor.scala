import _root_.util.Logging
import akka.actor.Actor
import models.dos.{TaskType, TaskState, Task}
import play.api.Logger
import processors._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class TaskQueueActor extends Actor with Logging {

  def receive = {

    case Look =>
      val head = Task.list(TaskState.QUEUED).headOption
      head foreach {
         task =>
          Task.start(task)
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
            Task.finish(task)
          }
      }
    case a@_ => Logger("DOS").error("huh? ==> " + a)


  }

}

case class Look()


