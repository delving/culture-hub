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

package core.search

import org.bson.types.ObjectId
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.impl.{StreamingUpdateSolrServer, CommonsHttpSolrServer}
import play.api.Play.current
import xml.Node
import org.apache.solr.common.SolrInputDocument
import play.api.Play
import org.apache.solr.client.solrj.response.{FacetField, UpdateResponse, QueryResponse}
import collection.JavaConverters._
import play.api.cache.Cache


/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/7/11 1:18 AM  
 */

trait SolrServer {

  def getSolrServer = SolrServer.solrServer

  def getStreamingUpdateServer = SolrServer.streamingUpdateServer

  def runQuery(query: SolrQuery): QueryResponse = SolrServer.solrServer.query(query)

}

object SolrServer {

  private val url = Play.configuration.getString("solr.baseUrl").getOrElse("http://localhost:8983/solr/core2")
  private val solrServer = new CommonsHttpSolrServer(url)
  solrServer.setSoTimeout(10000) // socket read timeout
  solrServer.setConnectionTimeout(100)
  solrServer.setDefaultMaxConnectionsPerHost(100)
  solrServer.setMaxTotalConnections(100)
  solrServer.setFollowRedirects(false) // defaults to false
  // allowCompression defaults to false.
  // Server side must support gzip or deflate for this to have any effect.
  solrServer.setAllowCompression(false)
  solrServer.setMaxRetries(0)
  // defaults to 0.  > 1 not recommended.

  private val streamingUpdateServer = new StreamingUpdateSolrServer(url, 2500, 5)
  streamingUpdateServer.setSoTimeout(10000) // socket read timeout
  streamingUpdateServer.setConnectionTimeout(100)
  streamingUpdateServer.setDefaultMaxConnectionsPerHost(100)
  streamingUpdateServer.setMaxTotalConnections(100)
  streamingUpdateServer.setFollowRedirects(false) // defaults to false
  streamingUpdateServer.setAllowCompression(false)
  streamingUpdateServer.setMaxRetries(0) // defaults to 0.  > 1 not recommended.

  def deleteFromSolrById(id: String): UpdateResponse = streamingUpdateServer.deleteById(id)
  def deleteFromSolrById(id: ObjectId): UpdateResponse = {
    val response = deleteFromSolrById(id.toString)
    commit()
    response
  }
  def deleteFromSolrByQuery(query: String) = {
    val response = streamingUpdateServer.deleteByQuery(query)
    commit()
    response
  }

  def indexSolrDocument(doc: SolrInputDocument) = streamingUpdateServer.add(doc)

  def pushToSolr(doc: SolrInputDocument) {
    indexSolrDocument(doc)
    commit()
  }

  def commit() {
    streamingUpdateServer.commit()
  }

  def rollback() {
    streamingUpdateServer.rollback()
  }

  def getSolrFrequencyItemList(node: Node): List[SolrFrequencyItem] = {
    node.nonEmptyChildren.filter(node => node.attribute("name") != None).map {
      f => SolrFrequencyItem(f.attribute("name").get.text, f.text.toInt)
    }.toList
  }

  val SOLR_FIELDS = "solrFields"

  def getSolrFields: List[SolrDynamicField] = {

    Cache.getOrElse(SOLR_FIELDS, 120) {
      computeSolrFields
    }

  }

  def computeSolrFields = {
    import xml.XML
    import java.net.URL
    val lukeUrl: URL = new URL("%s/admin/luke".format(url))
    val fields = XML.load(lukeUrl) \\ "lst"

    fields.filter(node => node.attribute("name") != None && node.attribute("name").get.text.equalsIgnoreCase("fields")).head.nonEmptyChildren.map {
      field => {
        val fieldName = field.attribute("name").get.text
        val fields = field.nonEmptyChildren
        fields.foldLeft(SolrDynamicField(name = fieldName))((sds, f) => {
          val text = f.attribute("name")
          text match {
            case Some(fieldtype) if (fieldtype.text == ("type")) => sds.copy(fieldType = f.text)
            case Some(fieldtype) if (fieldtype.text == ("index")) => sds.copy(index = f.text)
            case Some(fieldtype) if (fieldtype.text == ("schema")) => sds.copy(schema = f.text)
            case Some(fieldtype) if (fieldtype.text == ("dynamicBase")) => sds.copy(dynamicBase = f.text)
            case Some(fieldtype) if (fieldtype.text == "docs") => sds.copy(docs = f.text.toInt)
            case Some(fieldtype) if (fieldtype.text == "distinct") => sds.copy(distinct = f.text.toInt)
            case Some(fieldtype) if (fieldtype.text == "topTerms") => sds.copy(topTerms = getSolrFrequencyItemList(f))
            case Some(fieldtype) if (fieldtype.text == "histogram") => sds.copy(histogram = getSolrFrequencyItemList(f))
            case _ => sds
          }
        }
        )
      }
    }.toList

  }

  def getFacetFieldAutocomplete(facetName: String,  facetQuery: String) = {
    val normalisedFacetName = "%s_lowercase".format(SolrBindingService.stripDynamicFieldLabels(facetName))
    val normalisedFacetQuery = if (normalisedFacetName.endsWith("_lowercase")) facetQuery.toLowerCase else facetQuery
    val query = new SolrQuery("*:*")
    query setFacet true
    query setFacetLimit 50
    query setFacetMinCount 1
//    query addFacetField (normalisedFacetName)
//    query setFacetPrefix (normalisedFacetName, normalisedFacetQuery)
    query addFacetField (facetName)
    query setFacetPrefix (facetName, facetQuery.capitalize) // split(" ") map(_.capitalize) mkString(" ")
    query setRows 0
    val response = solrServer query (query)
    val facetValues = (response getFacetField (facetName))
//    val facetValuesLowerCase = (response getFacetField (normalisedFacetName))

    if (facetValues.getValueCount != 0) facetValues.getValues.asScala.take(10) else List[FacetField.Count]()
  }

}

case class SolrDynamicField(name: String, fieldType: String = "text", schema: String = "", index: String = "", dynamicBase: String = "", docs: Int = 0, distinct: Int = 0, topTerms: List[SolrFrequencyItem] = List.empty, histogram: List[SolrFrequencyItem] = List.empty) {

  lazy val fieldCanBeUsedAsFacet: Boolean = name.endsWith("_facet") || fieldType.equalsIgnoreCase("string") || name.startsWith("facet_")
  lazy val fieldIsSortable: Boolean = name.startsWith("sort_")

  lazy val xmlFieldName = normalisedField.replaceFirst("_", ":")
  lazy val normalisedField = SolrBindingService.stripDynamicFieldLabels(name)
}

case class SolrFrequencyItem(name: String, freq: Int)

