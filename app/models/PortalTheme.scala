package models

import eu.delving.metadata.RecordDefinition
import com.novus.salat.dao.SalatDAO
import com.mongodb.casbah.commons.Imports._
import models.salatContext._
import com.mongodb.casbah.commons.MongoDBObject
import cake.ComponentRegistry
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
                       baseUrl:                             String,
                       displayName:                         String,
                       googleAnalyticsTrackingCode:         Option[String] = Some(""),
                       addThisTrackingCode:                 Option[String] = Some(""),
                       defaultLanguage:                     String = "en",
                       colorScheme:                         String = "azure",
                       solrSelectUrl:                       String = "http://localhost:8983/solr",
                       cacheUrl:                            String = "http://localhost:8983/services/image?",
                       emailTarget:                         EmailTarget = EmailTarget(),
                       homePage:                            Option[String] = Some(""),
                       metadataPrefix:                      Option[String] = Some("")) {

  def getRecordDefinition: RecordDefinition = {
    try {
      ComponentRegistry.metadataModel.getRecordDefinition(metadataPrefix.get)
    }
    catch {
      case ex: Exception => ComponentRegistry.metadataModel.getRecordDefinition
    }
  }

}

object PortalTheme extends SalatDAO[PortalTheme, ObjectId](collection = portalThemeCollection) {

  def findAll = {
    find(MongoDBObject()).toList
  }

}