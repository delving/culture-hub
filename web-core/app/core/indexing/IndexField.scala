package core.indexing

import org.apache.solr.common.SolrInputDocument

/**
 * Fixed set of fields used during indexing and search
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
case class IndexField(key: String) {
  val xmlKey = key.replaceFirst("_", ":")
}

case object IndexField {

  // ~~~ system fields
  val ID = IndexField("id")
  val HUB_ID = IndexField("delving_hubId")
  val ORG_ID = IndexField("delving_orgId")

  val SCHEMA = IndexField("delving_currentSchema")
  val ALL_SCHEMAS = IndexField("delving_allSchemas")

  val RECORD_TYPE = IndexField("delving_recordType")
  val HAS_DIGITAL_OBJECT = IndexField("delving_hasDigitalObject")

  val FULL_TEXT_OBJECT_URL = IndexField("delving_fullTextObjectUrl")
  val FULL_TEXT = IndexField("delving_fullText")

  val IMAGE_URL = IndexField("delving_imageUrl")

  // ~~ legacy
  val EUROPEANA_URI = IndexField("europeana_uri")
  val PMH_ID = IndexField("delving_pmhId")
  val VISIBILITY = IndexField("delving_visibility")

  implicit def withRichSolrInputDocument(doc: SolrInputDocument) = new {

    def +=(pair: (IndexField, AnyRef)) {
      doc.addField(pair._1.key, pair._2)
    }

  }

}


