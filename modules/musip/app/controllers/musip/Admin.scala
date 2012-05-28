package controllers.musip

import controllers.OrganizationController
import play.api.mvc.Action
import play.api.Play.current
import org.apache.solr.common.SolrInputDocument
import core.Constants._
import core.indexing.IndexingService
import scala.xml._
import models.{MetadataCache, MetadataItem}
import play.api.libs.ws.WS
import play.api.{Logger, Play}
import java.util.concurrent.TimeUnit

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
        val syncedMuseums = museums.map(r => scala.xml.XML.loadString(r) \ "actor").map(sync(_, orgId, "museum", {
              museum =>
                Map(
                    TITLE -> List((museum \ "name").text.trim),
                    DESCRIPTION -> List((museum \ "description").text.trim),
                    THUMBNAIL -> List((museum \ "image").text.trim)
                   )
            })).getOrElse(0)

        val collections = WS.url(collectionUrl).get().await(timeout, TimeUnit.SECONDS).fold(t => { log.error("Could not sync collections at URL " + collectionUrl); None }, r => Some(r.body))
        val syncedCollections = collections.map(r => scala.xml.XML.loadString(r) \ "collection").map(sync(_, orgId, "collection", {
          collection =>
            Map(
                TITLE -> List((collection \ "name").text.trim),
                DESCRIPTION -> List((collection \ "description").text.trim),
                THUMBNAIL -> (collection \ "images" \ "url").map(_.text.trim).toList
               )
        })).getOrElse(0)

        Ok("Synchronized %s museums and %s collections".format(syncedMuseums, syncedCollections))
    }
  }

  private def sync(items: NodeSeq, orgId: String, itemType: String, extractSystemFields: NodeSeq => MultiMap): Int = {
    IndexingService.deleteByQuery("delving_orgId:%s delving_recordType:%s delving_systemType:hubItem".format(orgId, itemType))
    for (item <- items.zipWithIndex) {
      val index = item._2
      val i = item._1
      val localId = (i \ "@localId").text
      val xml = i.toString()

      // extract system fields
      val systemFields = extractSystemFields(i)

      val metadataItem = MetadataItem(collection = "musip", itemId = localId, itemType = itemType, xml = Map("musip" -> xml), systemFields = systemFields, index = index)
      MetadataCache.get(orgId, "musip", itemType).saveOrUpdate(metadataItem)

      val doc = new SolrInputDocument()

      systemFields.foreach {
        sf => sf._2.foreach(v => doc.addField(sf._1, v))
      }

      doc.addField(ID, "%s_%s_%s".format(orgId, itemType, localId))
      doc.addField(ORG_ID, orgId)
      doc.addField(RECORD_TYPE, itemType)
      doc.addField(SYSTEM_TYPE, HUB_ITEM)
      doc.addField(HUB_URI, "/%s/%s/%s".format(orgId, itemType, localId))

      IndexingService.stageForIndexing(doc)
    }
    IndexingService.commit()
    items.size
  }


}
