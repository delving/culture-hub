package controllers.musip

import controllers.OrganizationController
import play.api.mvc.Action
import play.api.Play
import play.api.Play.current
import models.MusipItem
import org.apache.solr.common.SolrInputDocument
import core.Constants._
import core.indexing.IndexingService
import scala.xml._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends OrganizationController {

  type MultiMap = Map[String, List[String]]

  def synchronize(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        val syncedMuseums = Play.application.resource("/musip_actors.xml").map(r => scala.xml.XML.load(r) \ "actor").map(sync(_, orgId, "museum", {
          museum =>
            Map(
                TITLE -> List((museum \ "name").text.trim),
                DESCRIPTION -> List((museum \ "description").text.trim),
                THUMBNAIL -> List((museum \ "image").text.trim)
               )
        })).getOrElse(0)

        val syncedCollections = Play.application.resource("/musip_collections.xml").map(r => scala.xml.XML.load(r) \ "collection").map(sync(_, orgId, "collection", {
          collection =>
            Map(
                TITLE -> List((collection \ "name").text.trim),
                DESCRIPTION -> List((collection \ "description").text.trim),
                THUMBNAIL -> List((collection \ "image").text.trim)
               )
        })).getOrElse(0)

        Ok("Synchronized %s museums and %s collections".format(syncedMuseums, syncedCollections))
    }
  }

  private def sync(items: NodeSeq, orgId: String, itemType: String, extractSystemFields: NodeSeq => MultiMap): Int = {
    IndexingService.deleteByQuery("delving_orgId:%s delving_recordType:%s delving_systemType:hubItem".format(orgId, itemType))
    for (i <- items) {
      val localId = (i \ "@localId").text
      val xml = i.toString()

      // extract system fields
      val systemFields = Map(
        TITLE -> List((i \ "name").text.trim),
        DESCRIPTION -> List((i \ "description").text.trim),
        THUMBNAIL -> List((i \ "image").text.trim)
      )

      val item = MusipItem(rawXml = xml, orgId = orgId, itemId = localId, itemType = itemType, systemFields = systemFields)
      MusipItem.saveOrUpdate(item, orgId, itemType)

      val doc = new SolrInputDocument()

      systemFields.foreach {
        sf => doc.addField(sf._1, sf._2)
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
