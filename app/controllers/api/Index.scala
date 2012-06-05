package controllers.api

import controllers.DelvingController
import play.api.mvc._
import play.api.libs.concurrent.Promise
import models.IndexItem
import scala.xml._
import collection.mutable.ListBuffer
import core.Constants._
import core.indexing.IndexingService
import com.mongodb.casbah.commons.MongoDBObject
import org.joda.time.format.ISODateTimeFormat
import play.api.Logger

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Index extends DelvingController {

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

          valid.foreach {
            item =>
              if(item.deleted) {
                IndexItem.remove(item.orgId, item.itemId, item.itemType)
                IndexingService.deleteByQuery("""id:%s_%s_%s""".format(item.orgId, item.itemType, item.itemId))
                deleted += 1
              } else {
                IndexItem.update(MongoDBObject("itemId" -> item.itemId, "orgId" -> orgId, "itemType" -> item.itemType), IndexItem._grater.asDBObject(item), true)
                IndexingService.stageForIndexing(item.toSolrDocument)
                indexed += 1
              }
          }
          IndexingService.commit()

          val invalidItems = invalid.map(i => <invalidItem><error>{i._1}</error><item>{i._2}</item></invalidItem>)

          <indexResponse>
            <totalItemCount>{valid.size + invalid.size}</totalItemCount>
            <indexedItemCount>{valid.filterNot(_.deleted).size}</indexedItemCount>
            <deletedItemCount>{valid.filter(_.deleted).size}</deletedItemCount>
            <invalidItemCount>{invalid.size}</invalidItemCount>
            <invalidItems>{invalidItems}</invalidItems>
          </indexResponse>

        } map {
          response => Ok(response)
        }

      }
    }
  }

  private def parseIndexRequest(orgId: String, root: NodeSeq): (List[IndexItem], List[(String, NodeSeq)]) = {
    val validItems = new ListBuffer[IndexItem]()
    val invalidItems = new ListBuffer[(String, NodeSeq)]()
    for(item <- root \\ "indexItem") {

      val requiredAttributes = Seq("itemId", "itemType")
      val hasRequiredAttributes = requiredAttributes.foldLeft(true) {
        (r, c) => r && item.attribute(c).isDefined
      }
      if(!hasRequiredAttributes) {
        invalidItems += "Item misses required attributes 'itemId' or 'itemType'" -> item
      } else {
        val itemId = item.attribute("itemId").get.text
        val itemType = item.attribute("itemType").get.text

        // TODO add more reserved values?
        if(itemType == MDR) {
          invalidItems += "Item uses reserved itemType value 'mdr'" -> item
        } else {
          val deleted = item.attribute("delete").map(_.text == "true").getOrElse(false)

          // TODO check more field syntax
          val invalidDates = item.nonEmptyChildren.filter(f => f.label == "field" && f.attribute("fieldType").isDefined && f.attribute("fieldType").get.head.text == "date") flatMap {
            f =>
              try {
                ISODateTimeFormat.dateTime().parseDateTime(f.text)
                None
              } catch {
                case t => Some(("Invalid date field '%s' with value '%s'".format((f \ "@name").text, f.text) -> item))
              }
          }

          if(!invalidDates.isEmpty) {
            invalidItems ++= invalidDates
          } else {
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
    }
    (validItems.toList, invalidItems.toList)

  }


}
