package controllers.musip

import controllers.OrganizationController
import play.api.mvc.Action
import play.api.Play
import play.api.Play.current
import models.MusipItem
import com.mongodb.casbah.Imports._
import org.apache.solr.common.SolrInputDocument
import core.Constants._
import core.indexing.IndexingService

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Admin extends OrganizationController {

  def synchronize(orgId: String) = OrgOwnerAction(orgId) {
    Action {
      implicit request =>
        val r = Play.application.resource("/musip_actors.xml")

        if (r.isDefined) {
          val url = r.get
          val actors = scala.xml.XML.load(url) \ "actor"

          IndexingService.deleteByQuery("delving_orgId:%s delving_recordType:museum delving_systemType:hubItem".format(orgId))

          for (actor <- actors) {
            val localId = (actor \ "@localId").text

            println(localId)

            val item = MusipItem(rawXml = actor.toString(), orgId = orgId, itemId = localId, itemType = "museum")
            MusipItem.saveOrUpdate(item, orgId, "museum")

            // indexing
            val name = (actor \ "name").text
            val description = (actor \ "description").text

            println(name)

            val doc = new SolrInputDocument()

            doc.addField(ID, "%s_%s_%s".format(orgId, "musip", localId))
            doc.addField(ORG_ID, orgId)
            doc.addField(RECORD_TYPE, "museum")
            doc.addField(VISIBILITY, "10")
            doc.addField(SYSTEM_TYPE, HUB_ITEM)
            doc.addField(TITLE, name)
            doc.addField(TITLE +"_string", name)
            doc.addField(DESCRIPTION, description)

            IndexingService.stageForIndexing(doc)

          }

          IndexingService.commit()

          Ok("Synchronized %s items".format(actors.size))
        } else {
          Ok("Found no data to synchronize")
        }
    }
  }

}
