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

    // mandatory fields
    doc.addField(ID, itemId)
    doc.addField(ORG_ID, orgId)
    if (!doc.containsKey(VISIBILITY)) {
      doc addField(VISIBILITY, "10") // set to public by default
    }
    doc.addField(RECORD_TYPE, itemType) // TODO should we really set the record-type to this or do we want to also set an additional flag so that we can find the documents back in solr???

    val fields = XML.loadString(rawXml).nonEmptyChildren.filter(_.label == "field")

    // content fields
    fields.filter(_.attribute("name").isDefined).foreach {
      field =>
        val name = (field \ "@name").text
        val dataType = field.attribute("fieldType").getOrElse("text")
        val isFacet = field.attribute("facet").isDefined && (field \ "@facet").text == "true"

        val indexFieldName = "%s_%s".format(name, dataType)

        doc.addField(indexFieldName, field.text)

        if(isFacet) {
          doc.addField(indexFieldName + "_facet", field.text)
        }
    }

    doc
  }

}

object IndexItem extends SalatDAO[IndexItem, ObjectId](collection = indexItemsCollection) {
  def remove(itemId: String, orgId: String, itemType: String) {
    remove(MongoDBObject("orgId" -> orgId, "itemId" -> itemId, "itemType" -> itemType))
  }
}