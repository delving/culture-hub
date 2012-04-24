package models

import mongoContext._
import util.Constants._
import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import com.mongodb.casbah.Imports._
import org.apache.solr.common.SolrInputDocument
import xml.XML

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

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
          val thumbnailEmpty = field.text.isEmpty
          if(doc.containsKey(HAS_DIGITAL_OBJECT) && !thumbnailEmpty) {
            doc.remove(HAS_DIGITAL_OBJECT)
            doc.addField(HAS_DIGITAL_OBJECT, true)
            doc.addField(HAS_DIGITAL_OBJECT + "_facet", true)
          } else if(!doc.containsKey(HAS_DIGITAL_OBJECT)) {
            doc.addField(HAS_DIGITAL_OBJECT, thumbnailEmpty)
            doc.addField(HAS_DIGITAL_OBJECT + "_facet", thumbnailEmpty)
          }
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
    if (!doc.containsKey(VISIBILITY)) {
      doc addField(VISIBILITY, "10") // set to public by default
    }
    doc.addField(SYSTEM_TYPE, INDEX_API_ITEM)
    doc.addField(RECORD_TYPE, itemType)
    doc.addField(RECORD_TYPE + "_facet", itemType)



    doc
  }

}

object IndexItem extends SalatDAO[IndexItem, ObjectId](collection = indexItemsCollection) {

  def findOneById(id: String) = {
    if(id.split("_").length != 3) {
      None
    } else {
      val Array(orgId, itemType, itemId) = id.split("_")
      findOne(MongoDBObject("orgId" -> orgId, "itemType" -> itemType, "itemId" -> itemId))
    }
  }

  def remove(itemId: String, orgId: String, itemType: String) {
    remove(MongoDBObject("orgId" -> orgId, "itemId" -> itemId, "itemType" -> itemType))
  }
}