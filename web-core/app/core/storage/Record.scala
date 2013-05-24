package core.storage

/**
 * A Record
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class Record(
  id: String, // persistent identifier
  schemaPrefix: String, // prefix of the schema
  document: String // the raw XML document
  )