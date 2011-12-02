package controllers.admin

import controllers.DelvingController
import play.mvc.results.Result
import jobs.UGCIndexing

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends DelvingController with AdminSecure {

  def indexUGC: Result = {

    if(request.isNew) {
      val task = new UGCIndexing().now()
      request.args.put("ugcIndexingTask", task)
      return WaitFor(task)
    }
    Text("Indexed all things")
  }

}