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
    root += (PARENT_PATH, "/")
    root += (TITLE, (doc \ "title").text)
    root += (DESCRIPTION, (doc \ "archdesc" \ "odd").text)

    val eadHeader = newMultiMap
    eadHeader += (ID, hubId.toString + "_" + "header")
    eadHeader += (ROOT_ID, hubId.toString)
    eadHeader += (PARENT_PATH, "/ead")
    eadHeader += ("ead_archdesc_did_unittitle", (doc \ "archdesc" \ "did_unittitle").text)
    eadHeader += ("ead_archdesc_did_unitdate", (doc \ "archdesc" \ "did_unitdate").text)
    eadHeader += ("ead_archdesc_did_origination_corpname", (doc \ "archdesc" \ "did_origination_corpname").text)
    eadHeader += ("ead_archdesc_odd", (doc \ "archdesc" \ "odd").text)
    eadHeader += ("ead_archdesc_arrangement", (doc \ "archdesc" \ "arrangement").text)

    val nodeDocuments = new ArrayBuffer[MultiMap]
    traverseNodes(doc \ "node", hubId.toString, (doc \ "key").text, nodeDocuments)

    val allDocuments = Seq(root, eadHeader) ++ nodeDocuments

    allDocuments
      .filter { f => f.contains(ID.key) && !f(ID.key).isEmpty }
      .filter { f => f.contains(TITLE.key) && !f(TITLE.key).isEmpty }
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
