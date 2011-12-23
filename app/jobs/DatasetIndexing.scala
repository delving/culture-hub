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

package jobs

import play.jobs.{Every, Job}
import components.Indexing
import models.{DataSetState, DataSet}
import play.Logger
import controllers.ErrorReporter

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 8/14/11 8:43 PM  
 */

@Every("10s")
class DataSetIndexing extends Job {

  override def doJob() {
    if (play.Play.started) {
      val dataSet = DataSet.findCollectionForIndexing()
      if (dataSet != None) {
        dataSet.get.getIndexingMappingPrefix match {
          case Some(prefix) =>
            try {
              Indexing.indexInSolr(dataSet.get, prefix)
            } catch {
              case t => {
                Logger.error(t, "Error while processing indexing for DataSet %s with mapping prefix %s".format(dataSet.get.spec, prefix))
                ErrorReporter.reportError(getClass.getName, t, "Error during indexing of DataSet")
                DataSet.updateState(dataSet.get, DataSetState.ERROR)
              }
            }
          case None =>
            Logger.error("No indexing mapping prefix set for DataSet " + dataSet.get.spec)
            ErrorReporter.reportError(getClass.getName, new RuntimeException(), "ENo indexing mapping prefix set for DataSet " + dataSet.get.spec)
            DataSet.updateState(dataSet.get, DataSetState.ERROR)
        }
      }
    }
  }

  override def onException(e: Throwable) {
    Logger.error(e, "Error during index of DataSet")
    ErrorReporter.reportError(getClass.getName, e, "Error during indexing of DataSet")
  }
}