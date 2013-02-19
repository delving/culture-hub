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

import _root_.util.OrganizationConfigurationHandler
import exceptions.SolrConnectionException
import org.apache.solr.client.solrj.SolrQuery
import xml.XML
import xml.Node
import org.apache.solr.client.solrj.response.{ FacetField, QueryResponse }
import collection.JavaConverters._
import play.api.cache.Cache
import org.apache.solr.client.solrj.impl.{ ConcurrentUpdateSolrServer, HttpSolrServer }
import java.net.URL
import models.OrganizationConfiguration
import play.api.Play.current

/**
 * REFACTORME:
 *
 * The SolrServer trait should go, and instead a SolrServerService (or so) should be used, taking into account the
 * OrganizationConfiguration design.
 *
 * Currently we cache the different SOLR servers on a per-resource basis, in order not to create duplicate resources (which may be expensive)
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <ernhardt.manuel@gmail.com>
 * @since 7/7/11 1:18 AM
 */

trait SolrServer {

  def getSolrServer(configuration: OrganizationConfiguration) = SolrServer.solrServer(configuration)

  def getStreamingUpdateServer(configuration: OrganizationConfiguration) = SolrServer.streamingUpdateServer(configuration)

  def runQuery(query: SolrQuery, retries: Int = 0)(implicit configuration: OrganizationConfiguration): QueryResponse = {
    try {
      SolrServer.solrServer(configuration).query(query)
    } catch {
      case e: SolrConnectionException =>
        if (retries < 3) {
          runQuery(query, retries + 1)
        } else {
          throw e
        }
    }
  }

}

object SolrServer {

  val SOLR_FIELDS_CACHE_KEY_PREFIX = "solrFields"

  private lazy val solrServers: Map[String, HttpSolrServer] = OrganizationConfigurationHandler.organizationConfigurations.map { configuration =>
    (configuration.solrBaseUrl -> {
      val solrServer = new HttpSolrServer(configuration.solrBaseUrl)
      solrServer.setSoTimeout(5000) // socket read timeout
      solrServer.setConnectionTimeout(5000)
      solrServer.setDefaultMaxConnectionsPerHost(64)
      solrServer.setMaxTotalConnections(125)
      solrServer.setFollowRedirects(false) // defaults to false
      solrServer.setAllowCompression(false)
      solrServer.setMaxRetries(1)
      solrServer
    })
  }.toMap

  private lazy val solrUpdateServers: Map[String, ConcurrentUpdateSolrServer] = OrganizationConfigurationHandler.organizationConfigurations.map {
    configuration =>
      (configuration.solrBaseUrl -> {
        new ConcurrentUpdateSolrServer(configuration.solrBaseUrl, 1000, 2)
      })
  }.toMap

  private[search] def solrServer(configuration: OrganizationConfiguration) = solrServers(configuration.solrBaseUrl)

  private[search] def streamingUpdateServer(configuration: OrganizationConfiguration) = solrUpdateServers.get(configuration.solrBaseUrl).getOrElse(
    throw new RuntimeException("Couldn't find cached SOLR update server for key '%s', available keys: %s".format(
      configuration.solrBaseUrl, solrUpdateServers.map(_._1).mkString(", ")
    )))

  def deleteFromSolrByQuery(query: String)(implicit configuration: OrganizationConfiguration) = {
    val response = streamingUpdateServer(configuration).deleteByQuery(query)
    streamingUpdateServer(configuration).commit()
    response
  }

  def getSolrFrequencyItemList(node: Node): List[SolrFrequencyItem] = {
    node.nonEmptyChildren.filter(node => node.attribute("name") != None).map {
      f => SolrFrequencyItem(f.attribute("name").get.text, f.text.toInt)
    }.toList
  }

  def getSolrFields(configuration: OrganizationConfiguration): List[SolrDynamicField] = {
    Cache.getOrElse(SOLR_FIELDS_CACHE_KEY_PREFIX + configuration.name, 120) {
      computeSolrFields(configuration)
    }

  }

  def computeSolrFields(configuration: OrganizationConfiguration) = {
    val lukeUrl: URL = new URL("%s/admin/luke".format(configuration.solrBaseUrl))
    val fields = XML.load(lukeUrl) \\ "lst"

    fields.filter(node => node.attribute("name") != None && node.attribute("name").get.text.equalsIgnoreCase("fields")).head.nonEmptyChildren.map {
      field =>
        {
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

  def getFacetFieldAutocomplete(facetName: String, facetQuery: String, facetLimit: Int = 10)(configuration: OrganizationConfiguration) = {
    val normalisedFacetName = "%s_lowercase".format(SolrBindingService.stripDynamicFieldLabels(facetName))
    val normalisedFacetQuery = if (normalisedFacetName.endsWith("_lowercase")) facetQuery.toLowerCase else facetQuery
    val query = new SolrQuery("*:*")
    query setFacet true
    query setFacetLimit facetLimit
    query setFacetMinCount 1
    query addFacetField (facetName)
    query setFacetPrefix (facetName, facetQuery.capitalize)
    query setRows 0
    val response = solrServer(configuration) query (query)
    val facetValues = (response getFacetField (facetName))

    if (facetValues.getValueCount != 0) facetValues.getValues.asScala else List[FacetField.Count]()
  }

}

case class SolrDynamicField(name: String, fieldType: String = "text", schema: String = "", index: String = "", dynamicBase: String = "", docs: Int = 0, distinct: Int = 0, topTerms: List[SolrFrequencyItem] = List.empty, histogram: List[SolrFrequencyItem] = List.empty) {

  lazy val fieldCanBeUsedAsFacet: Boolean = name.endsWith("_facet") || fieldType.equalsIgnoreCase("string") || name.startsWith("facet_")
  lazy val fieldIsSortable: Boolean = name.startsWith("sort_")

  lazy val xmlFieldName = normalisedField.replaceFirst("_", ":")
  lazy val normalisedField = SolrBindingService.stripDynamicFieldLabels(name)
}

case class SolrFrequencyItem(name: String, freq: Int)

