package models

import com.novus.salat.dao.SalatDAO
import org.bson.types.ObjectId
import org.apache.solr.common.SolrInputDocument
import core.Constants._
import core.search.SolrServer
import java.util.Date
import xml.NodeSeq
import models.mongoContext._
import core.indexing.IndexingService

case class DrupalEntity(_id: ObjectId = new ObjectId, rawXml: String, id: DrupalEntityId, enrichments: Map[String, Array[String]] = Map.empty, deleted: Boolean = false) {

  import xml.{Node, XML}

  def createSolrFieldLabel(node: Node): String = "%s_%s".format(node.prefix, node.label)

  def nodeToField(node: Node, doc: SolrInputDocument) {
    def addField(indexType: String) {
      doc addField("%s_%s".format(createSolrFieldLabel(node), indexType), node.text)
    }

    val attrMap = node.attributes.asAttrMap
    val fieldType = attrMap.get("type").getOrElse("text")
    fieldType match {
      case "location" => addField("location")
      case "date" => addField("date")
      case "link" => addField("link")
      case "int" => addField("int")
      case "text" => addField("text")
      case "string" => addField("string")
      case _ => addField("text")
    }
    if (attrMap.contains("facet") && attrMap.get("facet").getOrElse("false") == "true")
      doc addField("%s_facet".format(createSolrFieldLabel(node)), node.text)
  }


  def toSolrDocument: SolrInputDocument = {
    import org.apache.solr.common.SolrInputDocument
    val doc = new SolrInputDocument
    doc addField(ID, id.nodeId)
    doc addField("drup_id_string", id.id)
    doc addField("drup_entityType_string", id.nodeType)
    doc addField("drup_bundle_string", id.bundle)
    doc addField(EUROPEANA_URI, id.nodeId)
    doc addField("europeana_collectionName_s", id.bundle)
    doc addField("europeana_provider_s", "ITIN")
    doc addField(ORG_ID, "ifthenisnow")
    doc addField(RECORD_TYPE, "drupal")
    doc addField(PMH_ID, "drupal_%s".format(_id))
    if (!doc.containsKey(VISIBILITY + "_string")) {
      doc remove (VISIBILITY + "_string")
      doc addField(VISIBILITY, "10") // set visibilty to true always if not set by Drupal
    }
    val fields = XML.loadString(rawXml).nonEmptyChildren
    // store fields
    fields.filter(node => node.label != "#PCDATA").foreach {
      node =>
        nodeToField(node, doc)
    }
    // store links
    DrupalEntity.createCoRefList(fields, id).foreach {
      coRef => {
        doc addField("itin_link_path_s", coRef.to.uri)
        doc addField("itin_link_pathAlias_s", coRef.to.title)
      }
    }
    doc
  }
}

case class DrupalEntityId(id: String, nodeId: String, nodeType: String, bundle: String)

object DrupalEntity extends SalatDAO[DrupalEntity, ObjectId](collection = drupalEntitiesCollecion) {

  import xml.{Elem, Node}

  def insertInMongoAndIndex(entity: DrupalEntity, links: List[CoReferenceLink]) {
    import com.mongodb.casbah.commons.MongoDBObject
    import com.mongodb.WriteConcern
    update(MongoDBObject("id.nodeId" -> entity.id.nodeId), DrupalEntity._grater.asDBObject(entity), true, false)
    if (!entity.deleted) IndexingService.stageForIndexing(entity.toSolrDocument) else IndexingService.deleteById(entity.id.nodeId)
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

  def createCoRef(fromUri: String, fromTitle: String, fromType: String, toElements: Map[String, String]): CoReferenceLink = {
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

  def processStoreRequest(xmlData: NodeSeq)(processBlock: (DrupalEntity, List[CoReferenceLink]) => Unit): StoreResponse = {
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
      IndexingService.commit()
      StoreResponse(records.length, recordCounter._2)
    }
    catch {
      case ex: Exception =>
        IndexingService.rollback()
        StoreResponse(0, 0, false, ex.getMessage)
    }
  }
}

case class StoreResponse(itemsParsed: Int = 0, coRefsParsed: Int = 0, success: Boolean = true, errorMessage: String = "")

case class CoReferenceLink(_id: ObjectId = new ObjectId,
                           link_uri: String, // uri
                           link_category: String, // maybe controlled list
                           who: LinkAuthor,
                           from: FromLink,
                           to: ToLink,
                           when: LinkCreation,
                           where: LinkOrigin,
                           why: LinkDescription
                            ) {
  def toXml: String = {
    "<link>implement the rest</link>"
  }

  //  def fromXml(xmlString: String): CoReferenceLink = {
  //    CoReferenceLink()
  //  }

}

object CoReferenceLink extends SalatDAO[CoReferenceLink, ObjectId](collection = CoRefCollecion) {}

case class LinkAuthor(userUri: String, name: String, organisation: String, role: String)

case class FromLink(uri: String, linkType: String, title: String, crmClass: String = "", extra: Map[String, Array[String]] = Map.empty)

case class ToLink(uri: String, linkType: String, title: String, crmClass: String = "", extra: Map[String, Array[String]] = Map.empty)

case class LinkCreation(creationDate: Date)

case class LinkOrigin(context: String, siteUri: String, siteType: String)

case class LinkDescription(linkType: String, value: String, note: String, quality: String)
