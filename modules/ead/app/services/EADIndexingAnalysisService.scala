package services

import core.{ IndexingService, HubId, IndexingAnalysisService }
import eu.delving.schema.SchemaVersion
import org.w3c.dom.Node
import util.EADSimplifier
import core.indexing.IndexField._
import core.SystemField._
import scala.collection.mutable.ArrayBuffer
import java.net.URLEncoder

/**
 * Prototype for indexing APENet EAD
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class EADIndexingAnalysisService extends IndexingAnalysisService {

  def prefix: String = "ead"

  def analyze(hubId: HubId, schemaVersion: SchemaVersion, document: Node): Seq[IndexingService#IndexDocument] = {

    val documentAsScala = asXml(document)

    val doc = EADSimplifier.simplify(documentAsScala)

    val root: MultiMap = newMultiMap

    root += (HUB_ID, hubId.toString)
    root += (ID, hubId.toString)
    root += (ROOT_ID, hubId.toString)
    root += (PARENT_PATH, "/ead")
    root += ("ead_eadheader_eadid_text", (doc \ "eadheader" \ "eadid").text)
    root += (TITLE, (doc \ "title").text)
    root += (DESCRIPTION, (doc \ "archdesc" \ "odd").text)

    val archDesc = newMultiMap
    archDesc += (ID, hubId.toString + "_" + "header")
    archDesc += (ROOT_ID, hubId.toString)
    archDesc += (PARENT_PATH, "/ead")
    archDesc += (TITLE, (doc \ "archdesc" \ "did_unittitle").text)
    archDesc += ("ead_archdesc_did_unitdate_text", (doc \ "archdesc" \ "did_unitdate").text)
    archDesc += ("ead_archdesc_did_origination_corpname_text", (doc \ "archdesc" \ "did_origination_corpname").text)
    archDesc += (DESCRIPTION, (doc \ "archdesc" \ "odd").text)
    archDesc += ("ead_archdesc_arrangement_text", (doc \ "archdesc" \ "arrangement").text)

    val nodeDocuments = new ArrayBuffer[MultiMap]

    // until we start implementing proper indexing for the series nodes, we don't index them here
    // traverseNodes(doc \ "node", hubId.toString, (doc \ "key").text, nodeDocuments)

    val allDocuments = Seq(root, archDesc) ++ nodeDocuments

    allDocuments
      .filter { f => f.contains(ID.key) && !f(ID.key).isEmpty }
      .map { doc: MultiMap => addHousekeepingFields(hubId, doc).toMap }

  }

  def traverseNodes(node: scala.xml.NodeSeq, rootId: String, parentPath: String, documents: ArrayBuffer[MultiMap]) {

    val doc = newMultiMap
    val childId: String = rootId + "_" + (node \ "id").head.text
    doc += (ROOT_ID, rootId)
    doc += (ID, childId)
    doc += (PARENT_PATH, parentPath)
    doc += (TITLE, (node \ "title").text)
    doc += ("ead_id", (node \ "id").head.text)
    doc += (DESCRIPTION, (node \ "odd").text)

    documents += doc

    (node \ "node") foreach { n =>
      traverseNodes(n, rootId, (n \ "key").text, documents)
    }
  }

  // TODO - eventually, we have to provide this via the core API, and thus make a part of Indexing part of the API
  // now we just experiment to get something on the screen
  def addHousekeepingFields(hubId: HubId, doc: MultiMap) = {
    doc += (OWNER, "The Dude")
    doc += (PROVIDER, "The Other Dude")
    doc += (ORG_ID, hubId.orgId)
    doc += (SCHEMA, "ead")
    doc += (RECORD_TYPE, "mdr")

    doc += (SPEC, hubId.spec)
    doc += (COLLECTION, hubId.spec) // TODO should be the name, instead

    doc
  }

}
