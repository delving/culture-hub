package core

import org.w3c.dom.Node
import eu.delving.schema.SchemaVersion
import scala.xml.parsing.NoBindingFactoryAdapter
import com.sun.org.apache.xalan.internal.xsltc.trax.DOM2SAX
import scala.collection.mutable
import core.indexing.IndexableField

/**
 * Analyzes a document to be indexed.
 *
 * TODO provide implicit enrichment for MultiMap (+=)
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait IndexingAnalysisService {

  /**
   * The prefix of the schema being handled by this analyzer
   */
  def prefix: String

  /**
   * Analyses a document and returns one or more indexing documents as a result
   * @param hubId the ID of the record to be analyzed
   * @param schemaVersion the version of the schema of the document
   * @param document the input document
   * @return one or more [[ core.IndexingService#IndexDocument ]] ready to be processed further and sent for indexing
   */
  def analyze(hubId: HubId, schemaVersion: SchemaVersion, document: Node): Seq[IndexingService#IndexDocument]

  /**
   * Convenience function to turn a W3C DOM into a Scala DOM - useful for implementations of this service
   */
  def asXml(dom: Node): scala.xml.Node = {
    val dom2sax = new DOM2SAX(dom)
    val adapter = new NoBindingFactoryAdapter
    dom2sax.setContentHandler(adapter)
    dom2sax.parse()
    adapter.rootElem
  }

  type MultiMap = mutable.HashMap[String, mutable.Set[Any]] with mutable.MultiMap[String, Any]

  implicit class RichMultiMap(multiMap: MultiMap) {
    def +=(key: IndexableField, value: String) = {
      multiMap.addBinding(key.key, value)
    }
    def +=(key: String, value: String) = {
      multiMap.addBinding(key, value)
    }
  }

  /**
   * Convenience function to create a Map that can be turned into an IndexDocument by calling toMap
   */
  def newMultiMap = new mutable.HashMap[String, mutable.Set[Any]] with mutable.MultiMap[String, Any]

}

