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

package core.indexing

import core.collection.{Collection, OrganizationCollectionInformation}
import extensions.HTTPClient
import org.apache.solr.common.SolrInputDocument
import play.api.Logger
import core.SystemField._
import core.indexing.IndexField._
import org.apache.commons.httpclient.methods.GetMethod
import java.io.{InputStream, FilenameFilter, File}
import org.apache.tika.sax.BodyContentHandler
import org.apache.tika.parser.pdf.PDFParser
import exceptions.SolrConnectionException
import core.search.SolrServer
import org.apache.tika.parser.ParseContext
import org.apache.tika.metadata.Metadata
import java.net.URLEncoder
import models.{OrganizationConfiguration, Visibility}
import org.apache.commons.lang.StringEscapeUtils
import core.HubId


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Indexing extends SolrServer {

  type IndexableCollection = Collection with OrganizationCollectionInformation

  def indexOne(collection: IndexableCollection, hubId: HubId, mapped: Map[String, List[Any]], metadataFormatForIndexing: String)(implicit configuration: OrganizationConfiguration): Either[Throwable, String] = {
    val doc = createSolrInputDocument(mapped)
    addDelvingHouseKeepingFields(doc, collection, hubId, metadataFormatForIndexing)
    try {
      IndexingService.stageForIndexing(doc)
    } catch {
      case t: Throwable => Left(new SolrConnectionException("Unable to add document to Solr", t))
    }
    Right("ok")
  }

  private def createSolrInputDocument(indexDoc: Map[String, List[Any]]): SolrInputDocument = {

    val doc = new SolrInputDocument
    indexDoc.foreach {
      entry =>
        val unMungedKey = entry._1
        entry._2.foreach {
          value =>
            val cleanValue = if (unMungedKey.endsWith("_text")) StringEscapeUtils.unescapeHtml(value.toString) else value.toString
            doc.addField(unMungedKey, cleanValue)
        }
    }
    doc
  }

  def addDelvingHouseKeepingFields(inputDoc: SolrInputDocument, dataSet: IndexableCollection, hubId: HubId, schemaPrefix: String)(implicit configuration: OrganizationConfiguration) {
    import scala.collection.JavaConversions._

    // mandatory fields
    inputDoc += (ORG_ID -> dataSet.getOwner)
    inputDoc += (HUB_ID -> URLEncoder.encode(hubId.toString, "utf-8"))
    inputDoc += (SCHEMA -> schemaPrefix)
    inputDoc += (RECORD_TYPE -> dataSet.itemType.itemType)
    inputDoc += (VISIBILITY -> Visibility.PUBLIC.value.toString)

    inputDoc.addField(SPEC.tag, "%s".format(dataSet.spec))
    inputDoc.addField(COLLECTION.tag, "%s".format(dataSet.getName))

    // for backwards-compatibility
    inputDoc += (PMH_ID -> URLEncoder.encode(hubId.toString, "utf-8"))

    // force the provider and dataProvider configured in the DataSet
    if(inputDoc.containsKey(PROVIDER.tag)) {
      inputDoc.remove(PROVIDER.tag)
      inputDoc.addField(PROVIDER.tag, dataSet.getProvider)
    }
    if(inputDoc.containsKey(OWNER.tag)) {
      inputDoc.remove(OWNER.tag)
      inputDoc.addField(OWNER.tag, dataSet.getDataProvider)
    }

    // deepZoom hack
    val DEEPZOOMURL: String = "delving_deepZoomUrl_string"
    val DEEPZOOM_PATH: String = "/iip/deepzoom"
    if(inputDoc.containsKey(DEEPZOOMURL)) {
      try {
        // http://some.delving.org/iip/deepzoom/mnt/tib/tiles/<orgId>/<spec>/<image>
        val url = inputDoc.get(DEEPZOOMURL).getFirstValue.toString
        val i = url.indexOf(DEEPZOOM_PATH)
        if(i > -1) {
          val tileSetPath = url.substring(i + DEEPZOOM_PATH.length(), url.length())
          val tileSetParentPath = tileSetPath.substring(0, tileSetPath.lastIndexOf(File.separator))
          val parent = new File(tileSetParentPath)
          val extensionIdx = if(tileSetPath.indexOf(".") > -1) tileSetPath.lastIndexOf(".") else tileSetPath.length()
          val imageIdx = tileSetPath.lastIndexOf(File.separator)
          val image = tileSetPath.substring(imageIdx + 1, extensionIdx)
          if(!(parent.exists() && parent.isDirectory)) {
            Logger("CultureHub").debug("No tile path %s for deepZoomUrl %s".format(tileSetParentPath, url))
            inputDoc.remove(DEEPZOOMURL)
          } else {
            val files = parent.listFiles(new FilenameFilter() {
              def accept(dir: File, name: String) = name.startsWith(image)
            })
            if(files.length == 0) {
              Logger("CultureHub").debug("No image in directory %s starting with %s for deepZoomUrl %s".format(tileSetParentPath, image, url))
              inputDoc.remove(DEEPZOOMURL)
            }
          }
        }
      } catch {
        case t: Throwable =>
          Logger("CultureHub").error("Error during deepZoomUrl check, deepZoomURL " + inputDoc.get(DEEPZOOMURL).getFirstValue, t)
          // in doubt, remove
          inputDoc.remove(DEEPZOOMURL)
      }
    }

    if (inputDoc.containsKey(ID.key)) inputDoc.remove(ID.key)
    inputDoc += (ID -> hubId.toString)

    // TODO remove this hack
    val uriWithTypeSuffix = EUROPEANA_URI.key + "_string"
    val uriWithTextSuffix = EUROPEANA_URI.key + "_text"
    if (inputDoc.containsKey(uriWithTypeSuffix)) {
      val uriValue: String = inputDoc.get(uriWithTypeSuffix).getFirstValue.toString
      inputDoc.remove(uriWithTypeSuffix)
      inputDoc.addField(EUROPEANA_URI.key, uriValue)
    } else if (inputDoc.contains(uriWithTextSuffix)) {
      val uriValue: String = inputDoc.get(uriWithTextSuffix).getFirstValue.toString
      inputDoc.remove(uriWithTextSuffix)
      inputDoc.addField(EUROPEANA_URI.key, uriValue)
    }

    // ALL_SCHEMAS used to contain all the public schemas for a set
    // however now, it is only the indexing schema
    // we may want to expose this again at some point, just now we need to be aware of its true meaning
    inputDoc += (ALL_SCHEMAS -> schemaPrefix)
  }

}

object TikaIndexer extends HTTPClient {

  val log = Logger("CultureHub")

  def getFullTextFromRemoteURL(url: String): Option[String] = {
    try {
      log.info("Retrieving document for indexing with Tika at " + url)
      Some(parseFullTextFromPdf(getObject(url)))
    }
    catch {
      case t: Throwable =>
        log.error("Unable to process digital object found at " + url)
        None
    }
  }

  def getObject(url: String): InputStream = {
    val method = new GetMethod(url)
    getHttpClient executeMethod (method)
    method.getResponseBodyAsStream
  }

  def parseFullTextFromPdf(input: InputStream): String = {
    val textHandler = new BodyContentHandler()
    val metadata = new Metadata()
    val parser = new PDFParser()
    parser.parse(input, textHandler, metadata, new ParseContext)
    input.close()
    log.debug("Found following text via Tika for indexing:\n\n" + textHandler.toString)
    textHandler.toString
  }
}