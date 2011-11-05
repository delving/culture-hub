package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 11/5/11 10:35 AM  
 */

case class DrupalEntity(_id: ObjectId = new ObjectId, rawXml: String, id: DrupalEntityId, enrichments: Map[String,  Array[String]] = Map.empty) {

  import org.apache.solr.common.SolrDocument
  import xml.{Node, XML}

  def createSolrFieldLabel(node: Node): String = "%s_%s".format(node.prefix, node.label)

  def nodeToField(node: Node, doc: SolrDocument) {
    def addField(indexType: String) {doc addField ("%s_%s".format(createSolrFieldLabel(node), indexType), node.text)}

    val fieldType = node.attributes.asAttrMap.get("type").getOrElse("text")
    fieldType match {
      case "location" => addField("l")
      case "date" => addField("tdt")
      case "link" => addField("s")
      case "int" => addField("i")
      case "text" => addField("text")
      case "string" => addField("s")
      case _ => addField("text")
    }
  }


  def toSolrDocument: SolrDocument = {
    val doc = new SolrDocument
    doc.setField("id", id.nodeId)
    doc.setField("europeana_collectionName", id.bundle)
    doc.setField("europeana_provider", "ITIN")
    doc.setField("delvingID", "itin:%s".format(_id))
    val fields = XML.loadString(rawXml).nonEmptyChildren
    // store fields
    fields.filter(node => node.label != "#PCDATA").foreach{
      node =>
            println(createSolrFieldLabel(node))
            nodeToField(node, doc)
    }
    // store links
    DrupalEntity.createCoRefList(fields, id).foreach{
      coRef => {
        doc addField ("itin_link_path_s", coRef.to.uri)
        doc addField ("itin_link_pathAlias_s", coRef.to.title)
      }
    }
    doc
  }
}

case class DrupalEntityId(nodeId: String,  nodeType: String,  bundle: String)

object DrupalEntity extends SalatDAO[DrupalEntity, ObjectId](collection = drupalEntitiesCollecion) {

  import xml.Node

  def createDrupalEntityId(attributes: Map[String, String]): DrupalEntityId = {
    val id = attributes.getOrElse("id", "0")
    val entityType = attributes.getOrElse("entitytype", "0")
    val bundle = attributes.getOrElse("bundle", "0")
    DrupalEntityId("%s/%s".format(entityType, id),
      entityType,
      bundle)
  }

  def createFieldLabel(node: Node): String = "%s:%s".format(node.prefix, node.label)

  def createCoRef(fromUri: String,  fromTitle: String, fromType: String, toElements: Map[String, String]): CoReferenceLink = {
    import java.util.Date
    // todo finish this properly
    val toType = toElements.get("bunle").getOrElse("unknown")
    val toUri = toElements.get("path").getOrElse("unknown")
    val toTitle = toElements.get("pathalias").getOrElse("unknown")
    CoReferenceLink(link_uri = "%s:%s".format(fromUri, toUri),
      link_category = "link",
      from = FromLink(fromUri, fromType, fromTitle),
      to = ToLink(toUri, toType, toTitle),
      who = LinkAuthor("1", "1", "1", "1"),
      when = LinkCreation(new Date()),
      where = LinkOrigin("1", "itin", "partner"),
      why = LinkDescription("isPartOf", "", "", "100%"))
  }

  def createCoRefList(fields: Seq[scala.xml.Node], recordId: DrupalEntityId): List[CoReferenceLink] = {
    val fromUri: String = fields.find(field => createFieldLabel(field).equalsIgnoreCase("drup:path")).get.text
    val fromTitle: String = fields.find(field => createFieldLabel(field).equalsIgnoreCase("drup:pathalias")).get.text

    fields.filter(node => List("itin:ref_persons", "itin:related_pvb", "itin:ref_period").contains("%s:%s".format(node.prefix, node.label))).map {
      link => {
        createCoRef(fromUri, fromTitle, recordId.nodeType, link.attributes.asAttrMap)
      }
    }.toList
  }

  def processStoreRequest(data: String)(processBlock: (DrupalEntity, List[CoReferenceLink]) => Unit): StoreResponse = {
    import xml.XML
    val xmlData = XML.loadString(data)
    val records = xmlData \\ "record"
    val recordCounter = records.foldLeft((0, 0)) {
      (counter, record) => {
        val id = createDrupalEntityId(record.attributes.asAttrMap)
        val coRefs = createCoRefList(record.nonEmptyChildren, id)
        val entity = DrupalEntity(rawXml = record.toString(), id = id)
        processBlock(entity, coRefs)
        (counter._1 + 1, counter._2 + coRefs.length)
      }
    }
    StoreResponse(records.length, recordCounter._2)
  }
}

case class StoreResponse(itemsParsed: Int, coRefsParsed: Int, success: Boolean = true, errorMessage: String = "")
