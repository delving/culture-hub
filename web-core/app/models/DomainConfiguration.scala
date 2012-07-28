package models {

import org.apache.solr.client.solrj.SolrQuery
import core.search.{SolrSortElement, SolrFacetElement}
import play.api.{Play, Logger}
import Play.current
import collection.JavaConverters._

/**
 * Holds configuration that is used when a specific domain is accessed. It overrides a default configuration.
 *
 * TODO replace RuntimeExceptions with logging + ConfigurationException
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DomainConfiguration(name:                        String,
                               orgId:                       String,
                               domains:                     List[String] = List.empty,
                               mongoDatabase:               String,
                               themeDir:                    String,
                               defaultLanguage:             String = "en",
                               siteName:                    Option[String],
                               siteSlogan:                  Option[String],
                               emailTarget:                 EmailTarget = EmailTarget(),
                               hiddenQueryFilter:           Option[String] = Some(""),
                               homePage:                    Option[String] = None,
                               facets:                      Option[String] = None, // dc_creator:crea:Creator,dc_type
                               sortFields:                  Option[String] = None, // dc_creator,dc_provider:desc
                               apiWsKey:                    Boolean = false) {

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

object DomainConfiguration {

  /**
   * Computes all domain configurations based on the default Play configuration mechanism.
   */
  def getAll = {
    val config = Play.configuration.getConfig("themes").get
      val allDomainConfigurations = config.keys.filterNot(_.indexOf(".") < 0).map(_.split("\\.").head).toList.distinct
      val configurations = allDomainConfigurations.map {
        configurationKey => {
          val configuration = config.getConfig(configurationKey).get
          DomainConfiguration(
            name = configurationKey,
            orgId = configuration.getString("orgId").getOrElse(throw new RuntimeException("Invalid configuration %s: no orgId provided".format(configurationKey))),
            domains = configuration.underlying.getStringList("domains").asScala.toList,
            mongoDatabase = configuration.getString("mongoDatabase").getOrElse(throw new RuntimeException("Invalid configuration %s: no mongoDatabase provided".format(configurationKey))),
            themeDir = configuration.getString("themeDir").getOrElse("default"),
            defaultLanguage = configuration.getString("defaultLanguage").getOrElse("en"),
            siteName = configuration.getString("siteName"),
            siteSlogan = configuration.getString("siteSlogan").orElse(Some("Delving CultureHub")),
            emailTarget = {
              val emailTarget = configuration.getConfig("emailTarget").get
              EmailTarget(
                adminTo = emailTarget.getString("adminTo").getOrElse("servers@delving.eu"),
                exceptionTo = emailTarget.getString("exceptionTo").getOrElse("servers@delving.eu"),
                feedbackTo = emailTarget.getString("feedbackTo").getOrElse("servers@delving.eu"),
                registerTo = emailTarget.getString("registerTo").getOrElse("servers@delving.eu"),
                systemFrom = emailTarget.getString("systemFrom").getOrElse("servers@delving.eu"),
                feedbackFrom = emailTarget.getString("feedbackFrom").getOrElse("servers@delving.eu")
              )
            },
            hiddenQueryFilter = configuration.getString("hiddenQueryFilter"),
            homePage = configuration.getString("homePage"),
            facets = configuration.getString("facets"),
            sortFields = configuration.getString("sortFields"),
            apiWsKey = configuration.getBoolean("apiWsKey").getOrElse(false)
          )
        }
      }.toList

    val duplicateOrgIds = configurations.groupBy(_.orgId).filter(_._2.size > 1)
    if(!duplicateOrgIds.isEmpty) {
      throw new RuntimeException("Found domain configurations that use the same orgId: " +
        duplicateOrgIds.map(t => t._1 + ": " + t._2.map(_.name).mkString(", ")).mkString(", ")
      )
    }

    if(Play.isTest) {
      configurations.map(c => c.copy(mongoDatabase = c.mongoDatabase + "-TEST"))
    } else {
      configurations
    }
  }

}

}