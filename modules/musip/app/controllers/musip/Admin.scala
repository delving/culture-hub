package controllers.musip

import controllers.OrganizationController
import play.api.mvc.Action
import play.api.Play.current
import org.apache.solr.common.SolrInputDocument
import core.SystemField._
import core.indexing.IndexingService
import core.indexing.IndexField._
import scala.xml._
import models.{MetadataCache, MetadataItem}
import play.api.libs.ws.WS
import play.api.{Logger, Play}
import java.util.concurrent.TimeUnit
import util.DomainConfigurationHandler

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends OrganizationController {

  type MultiMap = Map[String, List[String]]

  val timeout = if(Play.isDev) 45 else 20

  val log = Logger("Musip")

  private val museumUrl = Play.application.configuration.getString("modules.musip.museumSourceUrl").getOrElse("NOT CONFIGURED!")
  private val collectionUrl = Play.application.configuration.getString("modules.musip.collectionSourceUrl").getOrElse("NOT CONFIGURED!")

  def synchronize(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        val museums = WS.url(museumUrl).get().await(timeout, TimeUnit.SECONDS).fold(t => { log.error("Could not sync museums at " + museumUrl); None } , r => Some(r.body))

        val extractMuseumSystemFields: NodeSeq => MultiMap = { museum =>
            Map(
                TITLE.tag -> List((museum \ "name").text.trim),
                DESCRIPTION.tag -> List((museum \ "description").text.trim),
                THUMBNAIL.tag -> List((museum \ "image").text.trim)
               )
        }

        val extractMuseumFacetFields: NodeSeq => Map[String, String] = { museum =>
          Map(
            "musip_municipality" -> (museum \ "municipality").text.trim,
            "musip_province" -> (museum \ "province").text.trim
          )

        }

        val syncedMuseums = museums.
                map(r => scala.xml.XML.loadString(r) \ "actor").
                map(sync(_, orgId, "museum", extractMuseumSystemFields, extractMuseumFacetFields)).
                getOrElse(0)

        val collections = WS.url(collectionUrl).get().await(timeout, TimeUnit.SECONDS).fold(t => { log.error("Could not sync collections at URL " + collectionUrl); None }, r => Some(r.body))

        val extractCollectionSystemFields: NodeSeq => MultiMap = { collection =>
            Map(
                TITLE.tag -> List((collection \ "name").text.trim),
                DESCRIPTION.tag -> List((collection \ "description").text.trim),
                THUMBNAIL.tag -> (collection \ "images" \ "url").map(_.text.trim).toList
               )
        }

        val extractCollectionFacetsFields: NodeSeq => Map[String, String] = { collection => Map.empty }

        val syncedCollections = collections.
                map(r => scala.xml.XML.loadString(r) \ "collection").
                map(sync(_, orgId, "collection", extractCollectionSystemFields, extractCollectionFacetsFields)).
                getOrElse(0)

        Ok("Synchronized %s museums and %s collections".format(syncedMuseums, syncedCollections))
    }
  }

  private def sync(items: NodeSeq, orgId: String, itemType: String, extractSystemFields: NodeSeq => MultiMap, extractFacetFields: NodeSeq => Map[String, String]): Int = {

    implicit val configuration = DomainConfigurationHandler.getByOrgId(orgId)

    IndexingService.deleteByQuery("delving_orgId:%s delving_recordType:%s delving_systemType:hubItem".format(orgId, itemType))
    for (item <- items.zipWithIndex) {
      val index = item._2
      val i = item._1
      val localId = (i \ "@localId").text
      val xml = i.toString()

      // extract system fields
      val systemFields = extractSystemFields(i)

      val metadataItem = MetadataItem(
        collection = "musip",
        itemId = localId,
        itemType = itemType,
        xml = Map("musip" -> xml),
        schemaVersions = Map("musip" -> "1.0.0"),
        systemFields = systemFields,
        index = index

      )
      MetadataCache.get(orgId, "musip", itemType).saveOrUpdate(metadataItem)

      val doc = new SolrInputDocument()

      systemFields.foreach {
        sf => sf._2.foreach(v => doc.addField(sf._1, v))
      }

      val hubId = "%s_%s_%s".format(orgId, itemType, localId)
      doc += (ID -> hubId)
      doc += (HUB_ID -> hubId)
      doc += (ORG_ID -> orgId)
      doc += (RECORD_TYPE -> itemType)

      // facets
      extractFacetFields(i).foreach { facetField =>
        doc.addField(facetField._1, facetField._2)
        doc.addField(facetField._1 + "_facet", facetField._2)
      }

      IndexingService.stageForIndexing(doc)(DomainConfigurationHandler.getByOrgId(orgId))
    }
    IndexingService.commit(configuration)
    items.size
  }


}
