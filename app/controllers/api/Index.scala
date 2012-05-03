package controllers.api

import controllers.DelvingController
import play.api.mvc._
import play.api.libs.concurrent.Promise
import scala.xml._
import collection.mutable.ListBuffer
import core.Constants._
import core.indexing.IndexingService
import models.{MetadataItem, MetadataCache}
import org.bson.types.ObjectId
import org.apache.solr.common.SolrInputDocument

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Index extends DelvingController {

  val CACHE_COLLECTION = "indexApiItems"

  def explain(path: List[String]) = path match {
    case Nil =>
      Some(
        ApiDescription("""The Index API makes it possible to send custom items to be indexed.
        |
        | It expects to receive an XML document containing one or more items to be indexed.
        | Each item must have an itemId attribute which serves as identifier for the item to be indexed,
        | as well as an itemType attribute which indicates the type of the item, to be used to filter it later on.
        |
        | An item contains one or more field elements that describe the data to be indexed. A field must provide a name attribute,
        | and can optionally specify:
        | - a fieldType attribute which is used by the indexing mechanism (default value: "text")
        | - a facet attribute which means that the field is to be made available as a facet (default value: false)
        |
        | The possible values for fieldType are: string, location, int, single, text, date, link
        |
        | For example:
        |
        | <indexRequest>
        |   <indexItem itemId="123" itemType="book">
        |     <field name="title" fieldType="string">The Hitchhiker's Guide to the Galaxy</field>
        |     <field name="author" fieldType="string" facet="true">Douglas Adams</field>
        |   </indexItem>
        | </indexRequest>
        |
        |
        | It is possible to remove existing items by specifying the delete flag:
        |
        | <indexRequest>
        |   <indexItem itemId="123" itemType="book" delete="true" />
        | </indexRequest>
        |
        |
        | Additionally, there is a number of optional system fields that can be specified, and that help to trigger additional functionality:
        |
        | <indexRequest>
        |   <indexItem itemId="123" itemType="book">
        |     <systemField name="thumbnail">http://path/to/thumbnail</field>
        |   </indexItem>
        | </indexRequest>
        |
        | The possible systemField names are: collection, thumbnail, landingPage, provider, owner, title, description, fullText
        """.stripMargin)
      )
    case _ => None
  }


  def status(orgId: String) = Action {
    implicit request =>
    // TODO provide some stats
      Ok
  }

  def submit(orgId: String) = Action(parse.tolerantXml) {
    implicit request => {
      Async {
        Promise.pure {

          val (valid, invalid) = parseIndexRequest(orgId, request.body)

          var indexed: Int = 0
          var deleted: Int = 0

          for(i <- valid.zipWithIndex) {
            val item = i._1
            val index = i._2
            val cache = MetadataCache.get(orgId, CACHE_COLLECTION, item.itemType)
            if(item.deleted) {
              cache.remove(item.itemId)
              IndexingService.deleteByQuery("""id:%s_%s_%s""".format(item.orgId, item.itemType, item.itemId))
              deleted += 1
            } else {
              val cacheItem = MetadataItem(collection = CACHE_COLLECTION, itemType = item.itemType, itemId = item.itemId, xml = Map("raw" -> item.rawXml), index = index)
              cache.saveOrUpdate(cacheItem)
              IndexingService.stageForIndexing(item.toSolrDocument)
              indexed += 1
            }
          }
          IndexingService.commit()

          <indexResponse>
            <totalItemCount>{valid.size + invalid.size}</totalItemCount>
            <indexedItemCount>{valid.filterNot(_.deleted).size}</indexedItemCount>
            <deletedItemCount>{valid.filter(_.deleted).size}</deletedItemCount>
            <invalidItemCount>{invalid.size}</invalidItemCount>
            <invalidItems>{invalid}</invalidItems>
          </indexResponse>

        } map {
          response => Ok(response)
        }

      }
    }
  }

  private def parseIndexRequest(orgId: String, root: NodeSeq): (List[IndexItem], List[NodeSeq]) = {
    val validItems = new ListBuffer[IndexItem]()
    val invalidItems = new ListBuffer[NodeSeq]()
    for(item <- root \\ "indexItem") {

      val requiredAttributes = Seq("itemId", "itemType")
      val hasRequiredAttributes = requiredAttributes.foldLeft(true) {
        (r, c) => r && item.attribute(c).isDefined
      }
      if(!hasRequiredAttributes) {
        invalidItems += item
      } else {
        val itemId = item.attribute("itemId").get.text
        val itemType = item.attribute("itemType").get.text

        // TODO add more reserved values?
        if(itemType == MDR) {
          invalidItems += item
        } else {
          val deleted = item.attribute("delete").map(_.text == "true").getOrElse(false)

          // TODO check the field syntax

          val indexItem = IndexItem(
            orgId = orgId,
            itemId = itemId,
            itemType = itemType,
            rawXml = item.toString(),
            deleted = deleted
          )
          validItems += indexItem
        }

      }
    }
    (validItems.toList, invalidItems.toList)

  }


}

case class IndexItem(_id: ObjectId = new ObjectId,
                     orgId: String,
                     itemId: String,
                     itemType: String,
                     rawXml: String,
                     deleted: Boolean = false) {

  def toSolrDocument: SolrInputDocument = {
    val doc = new SolrInputDocument

    val document = XML.loadString(rawXml).nonEmptyChildren
    val fields = document.filter(_.label == "field")

    // content fields
    fields.filter(_.attribute("name").isDefined).foreach {
      field =>
        val name = (field \ "@name").text
        val dataType = field.attribute("fieldType").getOrElse("text")
        val isFacet = field.attribute("facet").isDefined && (field \ "@facet").text == "true"

        val indexFieldName = "%s_%s".format(name, dataType)

        doc.addField("custom_%s".format(indexFieldName), field.text)

        if(isFacet) {
          doc.addField(indexFieldName + "_facet", field.text)
        }
    }

    // system fields
    val allowedSystemFields = List("collection", "thumbnail", "landingPage", "provider", "dataProvider")

    val systemFields = document.filter(_.label == "systemField")
    systemFields.filter(f => f.attribute("name").isDefined && allowedSystemFields.contains(f.attribute("name").get.text)).foreach {
      field =>
        val name = (field \ "@name").text

        if(name == "thumbnail") {
          doc.addField(THUMBNAIL, field.text)
        } else {
          val indexFieldName = "delving_%s_%s".format(name, "string")
          doc.addField(indexFieldName, field.text)
        }
    }

    // mandatory fields
    val id = "%s_%s_%s".format(orgId, itemType, itemId)
    doc.addField(ID, id)
    doc.addField(HUB_ID, id)
    doc.addField(ORG_ID, orgId)

    doc.addField(SYSTEM_TYPE, INDEX_API_ITEM)
    doc.addField(RECORD_TYPE, itemType)

    doc
  }

}
