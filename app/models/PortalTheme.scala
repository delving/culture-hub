package models

import com.novus.salat.dao.SalatDAO
import com.mongodb.casbah.commons.Imports._
import models.salatContext._
import cake.ComponentRegistry
import eu.delving.metadata.MetadataModelImpl
import controllers.search.SolrFacetElement
import controllers.search.SolrSortElement

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class PortalTheme(_id:                                 ObjectId = new ObjectId,
                       name:                                String,
                       templateDir:                         String,
                       isDefault:                           Boolean = false,
                       localiseQueryKeys:                   List[String] = List(),
                       hiddenQueryFilter:                   Option[String] = Some(""),
                       subdomain:                           Option[String] = None,
                       displayName:                         String,
                       googleAnalyticsTrackingCode:         Option[String] = Some(""),
                       addThisTrackingCode:                 Option[String] = Some(""),
                       defaultLanguage:                     String = "en",
                       colorScheme:                         String = "azure",
                       solrSelectUrl:                       String = "http://localhost:8983/solr",
                       cacheUrl:                            String = "http://localhost:8983/services/image?",
                       emailTarget:                         EmailTarget = EmailTarget(),
                       homePage:                            Option[String] = None,
                       metadataPrefix:                      Option[String] = None,
                       facets:                              Option[String] = None, // dc_creator:crea:Creator,dc_type
                       sortFields:                          Option[String] = None, // dc_creator,dc_provider:desc
                       apiWsKey:                            Boolean = false,
                       text:                                String = "") {



  def getRecordDefinition: eu.delving.metadata.RecordDefinition = {
      ComponentRegistry.metadataModel.asInstanceOf[MetadataModelImpl].getRecordDefinition(metadataPrefix.get)
  }

  def getFacets: List[SolrFacetElement] = {
    facets.getOrElse("").split(",").filter(k => k.split(":").size > 0 && k.split(":").size < 4).map {
      entry => {
        val k = entry.split(":")
        k.length match {
          case 1 | 2 => SolrFacetElement(k.head, k.head.take(4), k.head)
          case 3 => SolrFacetElement(k(1), k(2), k(3))
        }
      }
    }.toList
  }

  def getSortFields: List[SolrSortElement] = {
    import org.apache.solr.client.solrj.SolrQuery
    sortFields.getOrElse("").split(",").filter(sf => sf.split(":").size > 0 && sf.split(":").size < 3).map {
      entry => {
        val k = entry.split(":")
        k.length match {
          case 1 => SolrSortElement(k.head)
          case 2 =>
            SolrSortElement(
              k(1),
              if (k(2).equalsIgnoreCase("desc")) SolrQuery.ORDER.desc else SolrQuery.ORDER.asc
            )
        }
      }
    }.toList
  }

}

object PortalTheme extends SalatDAO[PortalTheme, ObjectId](collection = portalThemeCollection) with Resolver[PortalTheme] {

  def removeAll() {
    remove(MongoDBObject())
  }
}