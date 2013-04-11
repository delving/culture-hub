package services

import core.{ IndexingService, HubId, IndexingAnalysisService }
import eu.delving.schema.SchemaVersion
import org.w3c.dom.Node
import util.EADSimplifier
import core.indexing.IndexField._
import core.SystemField._
import scala.collection.mutable.ArrayBuffer

/**
 * Prototype for indexing APENet EAD
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class EADIndexingAnalysisService extends IndexingAnalysisService {

  def prefix: String = "ead"

  def analyze(hubId: HubId, schemaVersion: SchemaVersion, document: Node): Seq[IndexingService#IndexDocument] = {

    val documentAsScala = asXml(document)

    val doc = EADSimplifier.simplify(documentAsScala)

    val root = newMultiMap

    val hubId = (doc \ "id").text
    root.addBinding(HUB_ID.key, hubId)
    root.addBinding(TITLE.key, (doc \ "title").text)
    root.addBinding(DESCRIPTION.key, (doc \ "archdesc" \ "odd").text)

    val eadHeader = newMultiMap
    eadHeader.addBinding(HUB_ID.key, (doc \ "id").text)
    eadHeader.addBinding("ead_archdesc_did_unittitle", (doc \ "archdesc" \ "did_unittitle").text)
    eadHeader.addBinding("ead_archdesc_did_unitdate", (doc \ "archdesc" \ "did_unitdate").text)
    eadHeader.addBinding("ead_archdesc_did_origination_corpname", (doc \ "archdesc" \ "did_origination_corpname").text)
    eadHeader.addBinding("ead_archdesc_odd", (doc \ "archdesc" \ "odd").text)
    eadHeader.addBinding("ead_archdesc_arrangement", (doc \ "archdesc" \ "arrangement").text)

    val nodeDocuments = new ArrayBuffer[IndexingService#IndexDocument]
    traverseNodes(doc \ "node", hubId, nodeDocuments)

    Seq(root.toMap, eadHeader.toMap) ++ nodeDocuments
  }

  def traverseNodes(node: scala.xml.NodeSeq, rootId: String, documents: ArrayBuffer[IndexingService#IndexDocument]) {

    val doc = newMultiMap
    doc.addBinding(HUB_ID.key, rootId)
    doc.addBinding(TITLE.key, node \ "title")
    doc.addBinding("ead_id", node \ "id")
    doc.addBinding("ead_date", node \ "date")
    doc.addBinding(DESCRIPTION.key, node \ "odd")

    documents += doc.toMap

    (node \ "node") foreach { n => traverseNodes(n, rootId, documents) }
  }

}
