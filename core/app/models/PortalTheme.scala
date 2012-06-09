package models {

import org.bson.types.ObjectId
import org.apache.solr.client.solrj.SolrQuery
import core.search.{SolrSortElement, SolrFacetElement}
import play.api.{Play, Logger}
import Play.current
import collection.JavaConverters._

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class PortalTheme(_id:                                 ObjectId = new ObjectId,
                       name:                                String,
                       subdomain:                           Option[String] = None,
                       domains:                             List[String] = List.empty,
                       themeDir:                            String,
                       defaultLanguage:                     String = "en",
                       siteName:                            Option[String],
                       siteSlogan:                          Option[String],
                       emailTarget:                         EmailTarget = EmailTarget(),
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

case class EmailTarget(adminTo: String = "test-user@delving.eu",
                       exceptionTo: String = "test-user@delving.eu",
                       feedbackTo: String = "test-user@delving.eu",
                       registerTo: String = "test-user@delving.eu",
                       systemFrom: String = "noreply@delving.eu",
                       feedbackFrom: String = "noreply@delving.eu")

object PortalTheme {

  def getAll = {
    val config = Play.configuration.getConfig("themes").get
      val allThemes = config.keys.filterNot(_.indexOf(".") < 0).map(_.split("\\.").head).toList.distinct
      allThemes.map {
        themeKey => {
          val theme = config.getConfig(themeKey).get
          PortalTheme(
            name = themeKey,
            domains = theme.underlying.getStringList("domains").asScala.toList,
            themeDir = theme.getString("themeDir").getOrElse("default"),
            defaultLanguage = theme.getString("defaultLanguage").getOrElse("en"),
            siteName = theme.getString("siteName"),
            siteSlogan = theme.getString("siteSlogan").orElse(Some("Delving CultureHub")),
            emailTarget = {
              val emailTarget = theme.getConfig("emailTarget").get
              EmailTarget(
                adminTo = emailTarget.getString("adminTo").getOrElse("servers@delving.eu"),
                exceptionTo = emailTarget.getString("exceptionTo").getOrElse("servers@delving.eu"),
                feedbackTo = emailTarget.getString("feedbackTo").getOrElse("servers@delving.eu"),
                registerTo = emailTarget.getString("registerTo").getOrElse("servers@delving.eu"),
                systemFrom = emailTarget.getString("systemFrom").getOrElse("servers@delving.eu"),
                feedbackFrom = emailTarget.getString("feedbackFrom").getOrElse("servers@delving.eu")
              )
            },
            hiddenQueryFilter = theme.getString("hiddenQueryFilter"),
            homePage = theme.getString("homePage"),
            facets = theme.getString("facets"),
            sortFields = theme.getString("sortFields"),
            apiWsKey = theme.getBoolean("apiWsKey").getOrElse(false)
          )
        }
      }.toList
    }

}

}