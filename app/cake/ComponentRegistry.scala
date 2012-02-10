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

package cake

import eu.delving.metadata.MetadataModel
import eu.delving.metadata.MetadataModelImpl
import models.{DataSet, RecordDefinition}

/**
 * This object uses the Cake pattern to manage Dependency Injection, see also
 * http://jonasboner.com/2008/10/06/real-world-scala-dependency-injection-di.html
 *
 * TODO due to the bytecode enhancements of Play that did not work well with nested classes, we moved most things out of there.
 * Re- think this after migration to Play2
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/6/11 4:36 PM  
 */

trait MetadataModelComponent {

  import eu.delving.metadata.MetadataModel

  val metadataModel: MetadataModel
}

trait MetaRepoComponent {

  //  val metaRepo: MetaRepo
}


// =======================
// instantiate the services in a module
object ComponentRegistry extends MetadataModelComponent with MetaRepoComponent {

  val metadataModel: MetadataModel = new MetadataModelImpl

  //  val metaRepo = new MetaRepoImpl
  //  metaRepo.setResponseListSize(Play.configuration.getProperty("services.pmh.responseListSize").trim)
  //  metaRepo.setHarvestStepSecondsToLive(180)

  init()

  def init() {
    try {
      val metadataModelImpl = metadataModel.asInstanceOf[MetadataModelImpl]
      metadataModelImpl.setFactDefinitionsFile(DataSet.getFactDefinitionFile)
      metadataModelImpl.setRecordDefinitionFiles(RecordDefinition.getRecordDefinitionFiles : _*)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        throw t
    }

  }

}