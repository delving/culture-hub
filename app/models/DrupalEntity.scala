package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import controllers.SolrServer
import com.sun.org.apache.xpath.internal.operations.Bool

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 11/5/11 10:35 AM  
 */

case class DrupalEntity(_id: ObjectId = new ObjectId, rawXml: String, id: DrupalEntityId, enrichments: Map[String,  Array[String]] = Map.empty, deleted: Boolean = false) {

  import xml.{Node, XML}
  import org.apache.solr.common.{SolrInputDocument, SolrDocument}

  def createSolrFieldLabel(node: Node): String = "%s_%s".format(node.prefix, node.label)

  def nodeToField(node: Node, doc: SolrInputDocument) {
    def addField(indexType: String) {doc addField ("%s_%s".format(createSolrFieldLabel(node), indexType), node.text)}

    val attrMap = node.attributes.asAttrMap
    val fieldType = attrMap.get("type").getOrElse("text")
    fieldType match {
      case "location" => addField("l")
      case "date" => addField("tdt")
      case "link" => addField("s")
      case "int" => addField("i")
      case "text" => addField("text")
      case "string" => addField("s")
      case _ => addField("text")
    }
    if (attrMap.contains("facet") && attrMap.get("facet").getOrElse("false") == "true")
      doc addField ("%s_facet".format(createSolrFieldLabel(node)), node.text)
  }


  def toSolrDocument: SolrInputDocument = {
    import org.apache.solr.common.SolrInputDocument
    val doc = new SolrInputDocument
    doc setField("id", id.nodeId)
    doc setField("drup_id_s", id.id)
    doc setField ("drup_entityType_s", id.nodeType)
    doc setField ("drup_bundle_s", id.bundle)
    doc setField ("europeana_uri", id.nodeId)
    doc setField ("europeana_collectionName", id.bundle)
    doc setField ("europeana_provider", "ITIN")
    doc setField ("delving_recordType", "drupal")
    doc setField ("delving_pmhId", "drupal_%s".format(_id))
    val fields = XML.loadString(rawXml).nonEmptyChildren
    // store fields
    fields.filter(node => node.label != "#PCDATA").foreach{
      node =>
//            println(createSolrFieldLabel(node))
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

case class DrupalEntityId(id: String, nodeId: String,  nodeType: String,  bundle: String)

object DrupalEntity extends SalatDAO[DrupalEntity, ObjectId](collection = drupalEntitiesCollecion) with SolrServer {

  import xml.{Elem, Node}

  def insertInMongoAndIndex(entity: DrupalEntity, links: List[CoReferenceLink]) {
    import com.mongodb.casbah.commons.MongoDBObject
    import com.mongodb.WriteConcern
    update(MongoDBObject("id.nodeId" -> entity.id.nodeId), entity, true, false, new WriteConcern())
    if (!entity.deleted) getStreamingUpdateServer.add(entity.toSolrDocument) else getStreamingUpdateServer.deleteById(entity.id.nodeId)
  }

  def createDrupalEntityId(attributes: Map[String, String]): DrupalEntityId = {
    val id = attributes.getOrElse("id", "0")
    val entityType = attributes.getOrElse("entitytype", "0")
    val bundle = attributes.getOrElse("bundle", "0")
    DrupalEntityId(id,
      "%s/%s".format(entityType, id),
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

    // todo add more link content types
    fields.filter(node => List("itin:ref_persons", "itin:related_pvb", "itin:ref_period").contains("%s:%s".format(node.prefix, node.label))).map {
      link => {
        createCoRef(fromUri, fromTitle, recordId.nodeType, link.attributes.asAttrMap)
      }
    }.toList
  }

  def processStoreRequest(xmlData: Elem)(processBlock: (DrupalEntity, List[CoReferenceLink]) => Unit): StoreResponse = {
    val records = xmlData \\ "record"
    try {
      val recordCounter = records.foldLeft((0, 0)) {
        (counter, record) => {
          val id = createDrupalEntityId(record.attributes.asAttrMap)
          val coRefs = createCoRefList(record.nonEmptyChildren, id)
          val entity = DrupalEntity(rawXml = record.toString(), id = id,
            deleted = if (record.attributes.asAttrMap.contains("deleted")) true else false
          )
          processBlock(entity, coRefs)
          (counter._1 + 1, counter._2 + coRefs.length)
        }
      }
      getStreamingUpdateServer.commit
      StoreResponse(records.length, recordCounter._2)
    }
    catch {
      case ex: Exception =>
        getStreamingUpdateServer.rollback()
        StoreResponse(0, 0, false, ex.getMessage)
    }
  }
}

case class StoreResponse(itemsParsed: Int = 0, coRefsParsed: Int = 0, success: Boolean = true, errorMessage: String = "")
