/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.admin

import controllers.DelvingController
import play.mvc.results.Result
import jobs.UGCIndexing
import play.mvc.Before
import models.{User, DataSetState, DataSet}

/**
 * Allmighty HubAdmin spot
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends DelvingController {

  @Before
  def checkHubAdmin(): Result = {
    val u = User.findByUsername(connectedUser) getOrElse(return Forbidden("Wrong user"))
    if(!u.isHubAdmin.getOrElse(false)) {
      reportSecurity("User %s tried to get access to hub administration".format(connectedUser))
      return Forbidden(&("user.secured.noAccess"))
    }
    Continue
  }

  def indexUGC: Result = {

    if(request.isNew) {
      val task = new UGCIndexing().now()
      request.args.put("ugcIndexingTask", task)
      return WaitFor(task)
    }
    Text("Indexed all things")
  }

  def indexDataSets: Result = {

    val reIndexable = DataSet.findByState(DataSetState.ENABLED).toList
    reIndexable foreach { r => DataSet.updateStateAndIndexingCount(r, DataSetState.QUEUED)}
    
    Text("Queued %s DataSets for indexing".format(reIndexable.size))
  }

}