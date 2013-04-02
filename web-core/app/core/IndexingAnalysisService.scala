package core

import org.w3c.dom.Node
import eu.delving.schema.SchemaVersion

/**
 * Analyzes a document to be indexed.
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

}
