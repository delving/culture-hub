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

package cake.metaRepo

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/7/11 9:27 AM  
 */

// todo delete this class

class MetaRepoImpl {

  import java.util.Date
  import models.{MetadataRecord, DataSet}
  import cake.metaRepo.PmhVerbType.PmhVerb

  def createDataSet(spec: String): DataSet = null

  def getDataSets: Iterable[_ <: DataSet] = null

  def getDataSet(spec: String): DataSet = null

  def getDataSetForIndexing(maxSimultaneous: Int): DataSet = null

  def getMetadataFormats: Set[_ <: MetadataFormat] = null

  def getMetadataFormats(id: String, accessKey: String): Set[_ <: MetadataFormat] = null

  def getFirstHarvestStep(verb: PmhVerb, set: String, from: Date, until: Date, metadataPrefix: String, accessKey: String): HarvestStep = null

  def getHarvestStep(resumptionToken: String, accessKey: String): HarvestStep = null

  def removeExpiredHarvestSteps {}

  def getRecord(identifier: String, metadataFormat: String, accessKey: String): MetadataRecord = null
}