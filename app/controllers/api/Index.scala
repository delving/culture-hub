package controllers.api

import controllers.DelvingController
import play.api.mvc._
import play.api.libs.concurrent.Promise
import models.IndexItem
import scala.xml._
import collection.mutable.ListBuffer
import util.Constants._
import core.indexing.IndexingService

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Index extends DelvingController {

  def explain(path: List[String]) = path match {
    case "proxy" :: Nil =>
      Some(
        ApiDescription("""The Index API makes it possible to send custom items to be indexed.
        |
        | It expects to receive an XML document containing one or more items to be indexed.
        | Each item must have an itemId attribute which serves as identifier for the item to be indexed,
        | as well as an itemType attribute which indicates the type of the item, to be used to filter it later on.
        |
        | An item contains one or more field elements that describe the data to be indexed. A field must provide a name attribute,
        | and can optionally specify:
        | - a fieldType attribute which is used during indexing (default value: "text")
        | - a facet attribute which means that the field is to be made available as a facet (default value: false)
        |
        |
        | For example:
        |
        | <indexRequest>
        |   <indexItem itemId="123" itemType="book">
        |     <field name="title" dataType="string">The Hitchhiker's Guide to the Galaxy</field>
        |     <field name="author" fieldType="string" facet="true">Douglas Adams</field>
        |   </indexItem>
        | </indexRequest>
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

          valid.foreach {
            item =>
              if(item.deleted) {
                IndexItem.remove(item.orgId, item.itemId, item.itemType)
                IndexingService.deleteByQuery("""delving_orgId:%s id:%s delving_recordType:%s""".format())
                deleted += 1
              } else {
                IndexItem.save(item)
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
          val deleted = item.attribute("deleted").map(_ == "true").getOrElse(false)

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
