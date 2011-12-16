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

package components

import com.mongodb.casbah.Imports._
import controllers.SolrServer
import eu.delving.sip.IndexDocument
import org.apache.solr.common.SolrInputDocument
import cake.ComponentRegistry
import org.apache.solr.client.solrj.response.UpdateResponse
import eu.delving.sip.MappingEngine
import scala.collection.JavaConversions.asJavaMap
import models._
import salatContext.connection
import java.lang.String
import java.io.{FilenameFilter, File}
import play.Logger
import com.novus.salat.grater
import util.Constants._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Indexing extends SolrServer with controllers.ModelImplicits {

  @throws(classOf[MappingNotFoundException])
  @throws(classOf[SolrConnectionException])
  def indexInSolr(dataSet: DataSet, metadataFormatForIndexing: String): (Int, Int) = {
    val records = DataSet.getRecords(dataSet).find(MongoDBObject())
    DataSet.updateState(dataSet, DataSetState.INDEXING)
    val mapping = dataSet.mappings.get(metadataFormatForIndexing)
    if (mapping == None) throw new MappingNotFoundException("Unable to find mapping for " + metadataFormatForIndexing)
    val engine: MappingEngine = new MappingEngine(mapping.get.recordMapping.getOrElse(""), asJavaMap(dataSet.namespaces), play.Play.classloader.getParent, ComponentRegistry.metadataModel)
    var state = DataSet.getStateBySpecAndOrgId(dataSet.spec, dataSet.orgId)

    if (state == DataSetState.INDEXING) {
      records foreach {
        record =>
          if (records.numSeen % 100 == 0) {
            DataSet.updateIndexingCount(dataSet, records.numSeen)
            state = DataSet.getStateBySpecAndOrgId(dataSet.spec, dataSet.orgId)
          }
          val mapped = Option(engine.executeMapping(record.getXmlString()))

          // cache the result of the mapping so that we can use it to present the record directly without re-running a mapping
          mapped match {
            case Some(mappedDocument) =>
              val c: DBObject = mappedDocument
              connection(DataSet.getRecordsCollectionName(dataSet)).update(MongoDBObject("_id" -> record._id), $set ("mappedMetadata.%s".format(metadataFormatForIndexing) -> c))
            case None => // do nothing
          }

          val res = indexOne(dataSet, record, mapped, metadataFormatForIndexing)
          res match {
            case Left(t) => throw t
            case Right(ok) => // continue
          }
      }
    }

    state match {
      case DataSetState.INDEXING =>
        DataSet.updateState(dataSet, DataSetState.ENABLED)
        getStreamingUpdateServer.commit()
      case _ =>
        //        getStreamingUpdateServer.rollback() // todo find out what this does
        println("deleting dataset from solr " + dataSet.spec)
        deleteFromSolr(dataSet)
    }
    println(engine.toString)
    (1, 0)
  }

  def indexOne(dataSet: DataSet, mdr: MetadataRecord, mapped: Option[IndexDocument], metadataFormatForIndexing: String): Either[Throwable, String] = {
    mapped match {
      case Some(indexDoc) =>
        val doc = createSolrInputDocument(indexDoc)
        addDelvingHouseKeepingFields(doc, dataSet, mdr, metadataFormatForIndexing)
        try {
          getStreamingUpdateServer.add(doc)
        } catch {
          case t: Throwable => Left(new SolrConnectionException("Unable to add document to Solr", t))
        }
      case None => // do nothing
    }
    Right("ok")
  }

  def indexOneInSolr(orgId: String, spec: String, mdr: MetadataRecord) = {
    DataSet.findBySpecAndOrgId(spec, orgId) match {
      case Some(dataSet) =>
        val mapping = dataSet.mappings.get(dataSet.getIndexingMappingPrefix.getOrElse(""))
        if (mapping == None) throw new MappingNotFoundException("Unable to find mapping for " + dataSet.getIndexingMappingPrefix.getOrElse("NONE DEFINED!"))
        val engine: MappingEngine = new MappingEngine(mapping.get.recordMapping.getOrElse(""), asJavaMap(dataSet.namespaces), play.Play.classloader.getParent, ComponentRegistry.metadataModel)
        val mapped = Option(engine.executeMapping(mdr.getXmlString()))
        indexOne(dataSet, mdr, mapped, dataSet.getIndexingMappingPrefix.getOrElse(""))
      case None =>
        Logger.warn("Could not index MDR")
    }

  }

  def deleteFromSolr(dataSet: DataSet) {
    val deleteResponse: UpdateResponse = getStreamingUpdateServer.deleteByQuery(SPEC + ":" + dataSet.spec)
    deleteResponse.getStatus
    getStreamingUpdateServer.commit
  }

  private def createSolrInputDocument(indexDoc: IndexDocument): SolrInputDocument = {
    import scala.collection.JavaConversions._

    val doc = new SolrInputDocument
    indexDoc.getMap.entrySet().foreach {
      entry =>
        val unMungedKey = entry.getKey // todo later unmunge the key with namespaces.replaceAll("_", ":")
        entry.getValue.foreach(
          value =>
            doc.addField(unMungedKey, value.toString)
        )
    }
    doc
  }

  def addDelvingHouseKeepingFields(inputDoc: SolrInputDocument, dataSet: DataSet, record: MetadataRecord, format: String) {
    import scala.collection.JavaConversions._
    import controllers.search.SolrBindingService

    inputDoc.addField(PMH_ID, "%s_%s".format(dataSet.spec, record._id.toString))
    inputDoc.addField(SPEC, "%s".format(dataSet.spec))
    inputDoc.addField(FORMAT, format)
    inputDoc.addField(RECORD_TYPE, MDR)
    inputDoc.addField(VISIBILITY, dataSet.visibility.value)
    val hubId = "%s_%s_%s".format(dataSet.orgId, dataSet.spec, record.localRecordKey)
    inputDoc.addField(HUB_ID, hubId)
    inputDoc.addField(ORG_ID, dataSet.orgId)
    val indexedKeys = inputDoc.keys.map(key => (SolrBindingService.stripDynamicFieldLabels(key), key)).toMap // to filter always index a facet with _facet .filter(!_.matches(".*_(s|string|link|single)$"))
    // add facets at indexing time
    dataSet.idxFacets.foreach {
      facet =>
        if (indexedKeys.contains(facet)) {
          val facetContent = inputDoc.get(indexedKeys.get(facet).get).getValues
          inputDoc addField("%s_facet".format(facet), facetContent)
        }
    }
    // adding sort fields at index time
    dataSet.idxSortFields.foreach {
      sort =>
        if (indexedKeys.contains(sort)) {
          inputDoc addField("sort_all_%s".format(sort), inputDoc.get(indexedKeys.get(sort).get))
        }
    }

    // user collections
    inputDoc.addField(COLLECTIONS, record.links.filter(_.linkType == Link.LinkType.PARTOF).map(_.value(USERCOLLECTION_ID)).toArray)

    // deepZoom hack
    val DEEPZOOMURL: String = "delving_deepZoomUrl"
    val DEEPZOOM_PATH: String = "/iip/deepzoom"
    if(inputDoc.containsKey(DEEPZOOMURL)) {
      // http://some.delving.org/iip/deepzoom/mnt/tib/tiles/" + spec + "/" + image
      val url = inputDoc.get(DEEPZOOMURL).getValue.toString
      val i = url.indexOf(DEEPZOOM_PATH)
      if(i > -1) {
        val tileSetPath = url.substring(i + DEEPZOOM_PATH.length(), url.length())
        val tileSetParentPath = tileSetPath.substring(0, tileSetPath.lastIndexOf(File.separator))
        val parent = new File(tileSetParentPath)
        val extensionIdx = if(tileSetPath.indexOf(".") > -1) tileSetPath.indexOf(".") else tileSetPath.length()
        val image = tileSetPath.substring(tileSetPath.lastIndexOf(File.separator) + 1, extensionIdx)
        if(!(parent.exists() && parent.isDirectory)) {
          Logger.debug("No tile path %s for deepZoomUrl %s", tileSetParentPath, url)
          inputDoc.remove(DEEPZOOMURL)
        } else {
          val files = parent.listFiles(new FilenameFilter() {
            def accept(dir: File, name: String) = name.startsWith(image)
          })
          if(files.length == 0) {
            Logger.debug("No image in directory %s starting with %s for deepZoomUrl %s", tileSetParentPath, image, url)
            inputDoc.remove(DEEPZOOMURL)
          }
        }
      }
    }
    
    if (inputDoc.containsKey(ID)) inputDoc.remove(ID)
    inputDoc.addField(ID, hubId)
    val hasDigialObject: Boolean = inputDoc.containsKey(THUMBNAIL) && !inputDoc.get(THUMBNAIL).getValues.isEmpty
    inputDoc.addField(HAS_DIGITAL_OBJECT, hasDigialObject)

    if (hasDigialObject) inputDoc.setDocumentBoost(1.4.toFloat)

    dataSet.getMetadataFormats(true).foreach(format => inputDoc.addField("delving_publicFormats", format.prefix))
    dataSet.getMetadataFormats(false).foreach(format => inputDoc.addField("delving_allFormats", format.prefix))
  }

}