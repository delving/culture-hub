package core.rendering

import xml.{ NodeSeq, Elem }

/**
 * Adds the possibility of mutating a record based on contextual information, at rendering time
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait RecordTransformer {

  /**
   * Transforms a record, given the original record and a context
   * @param record the record to be transformed
   * @param context the rendering context
   * @return a mutated XML record
   */
  def transformRecord(record: NodeSeq, context: RenderingContext): NodeSeq

}

case class RenderingContext(
  parameters: Map[String, Seq[String]])
