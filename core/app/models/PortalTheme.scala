package models {

import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import play.api.Logger
import com.novus.salat.dao.SalatDAO
import org.apache.solr.client.solrj.SolrQuery
import mongoContext._
import core.search.{SolrSortElement, SolrFacetElement}

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class EmailTarget(adminTo: String = "test-user@delving.eu",
                       exceptionTo: String = "test-user@delving.eu",
                       feedbackTo: String = "test-user@delving.eu",
                       registerTo: String = "test-user@delving.eu",
                       systemFrom: String = "noreply@delving.eu",
                       feedbackFrom: String = "noreply@delving.eu") {

 }


case class PortalTheme(_id:                                 ObjectId = new ObjectId,
                       name:                                String,
                       subdomain:                           Option[String] = None,
                       domains:                             List[String] = List.empty,
                       themeDir:                            String,
                       defaultLanguage:                     String = "en",
                       siteName:                            Option[String],
                       siteSlogan:                          Option[String],
                       solrSelectUrl:                       String = "http://localhost:8983/solr",
                       cacheUrl:                            String = "http://localhost:8983/services/image?",
                       emailTarget:                         EmailTarget = EmailTarget(),
                       localiseQueryKeys:                   List[String] = List(),
                       hiddenQueryFilter:                   Option[String] = Some(""),
                       homePage:                            Option[String] = None,
                       facets:                              Option[String] = None, // dc_creator:crea:Creator,dc_type
                       sortFields:                          Option[String] = None, // dc_creator,dc_provider:desc
                       apiWsKey:                            Boolean = false) {

  def getFacets: List[SolrFacetElement] = {
    facets.getOrElse("").split(",").filter(k => k.split(":").size > 0 && k.split(":").size < 4).map {
      entry => {
        val k = entry.split(":")
        k.length match {
          case 1 => SolrFacetElement(k.head, k.head)
          case 2 => SolrFacetElement(k(0), k(1))
          case 3 =>
            try {
              SolrFacetElement(k(0), k(1), k(2).toInt)
            } catch {
              case  _ : java.lang.NumberFormatException =>
                Logger("CultureHub").warn("Wrong value %s for facet display column number for theme %s".format(k(2), name))
                SolrFacetElement(k(0), k(1))
            }
        }
      }
    }.toList
  }

  def getSortFields: List[SolrSortElement] = {
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

  def findAll = find(MongoDBObject()).toList

  def removeAll() {
    remove(MongoDBObject())
  }
}

}