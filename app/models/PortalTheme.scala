package models

import com.novus.salat.dao.SalatDAO
import com.mongodb.casbah.commons.Imports._
import models.salatContext._
import com.mongodb.casbah.commons.MongoDBObject
import cake.ComponentRegistry
import eu.delving.metadata.MetadataModelImpl

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
                       homePage:                            Option[String] = Some(""),
                       metadataPrefix:                      Option[String] = None,
                       facets:                              List[FacetElement] = List(),
                       apiWsKey:                            Boolean = false,
                       text:                                String = "") {

  def getRecordDefinition: eu.delving.metadata.RecordDefinition = {
      ComponentRegistry.metadataModel.asInstanceOf[MetadataModelImpl].getRecordDefinition(metadataPrefix.get)
  }

}

case class FacetElement(facetName: String, facetPrefix: String, facetPresentationName: String)

object PortalTheme extends SalatDAO[PortalTheme, ObjectId](collection = portalThemeCollection) with Resolver[PortalTheme] with Commons[PortalTheme]