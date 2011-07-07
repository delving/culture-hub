package models

import eu.delving.metadata.RecordDefinition
import com.novus.salat.dao.SalatDAO
import com.mongodb.casbah.commons.TypeImports._
import com.mongodb.casbah.MongoConnection
import models.salatContext._

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class PortalTheme(name: String,
                       templateDir: String,
                       isDefault: Boolean = false,
                       localiseQueryKeys: Array[String] = Array(),
                       hiddenQueryFilter: String = "",
                       baseUrl: String = "",
                       displayName: String = "default",
                       googleAnalyticsTrackingCode: String = "",
                       addThisTrackingCode: String = "",
                       defaultLanguage: String = "en",
                       colorScheme: String = "azure",
                       solrSelectUrl: String = "http://localhost:8983/solr",
                       cacheUrl: String = "http://localhost:8983/services/image?",
                       emailTarget: EmailTarget = EmailTarget(),
                       homePage: String = "",
                       metadataPrefix: String = "",
                       recordDefinition: RecordDefinition)

object PortalTheme extends SalatDAO[PortalTheme, ObjectId](collection = portalThemeCollection)