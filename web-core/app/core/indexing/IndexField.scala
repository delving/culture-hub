package core.indexing

import org.apache.solr.common.SolrInputDocument

/**
 * Fixed set of fields used during indexing and search
 *
 * TODO add SOLR type here.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
case class IndexField(key: String) extends IndexableField {
  val xmlKey = key.replaceFirst("_", ":")
}

trait IndexableField {

  def key: String

}

case object IndexField {

  val ID = IndexField("id")
  val HUB_ID = IndexField("delving_hubId")
  val ORG_ID = IndexField("delving_orgId")

  val SCHEMA = IndexField("delving_currentSchema")
  val ALL_SCHEMAS = IndexField("delving_allSchemas")

  val RECORD_TYPE = IndexField("delving_recordType")

  val HAS_DIGITAL_OBJECT = IndexField("delving_hasDigitalObject")

  val FULL_TEXT_OBJECT_URL = IndexField("delving_fullTextObjectUrl")
  val FULL_TEXT = IndexField("delving_fullText")

  val GEOHASH = IndexField("delving_geohash")
  val HAS_GEO_HASH = IndexField("delving_hasGeoHash")

  // TODO review this one.
  val ADDRESS = IndexField("delving_address")

  // ~~ legacy
  val EUROPEANA_URI = IndexField("europeana_uri")
  val PMH_ID = IndexField("delving_pmhId")

  // ~~~ deprecated
  val VISIBILITY = IndexField("delving_visibility")

  implicit def withRichSolrInputDocument(doc: SolrInputDocument) = new {

    def +=(pair: (IndexableField, AnyRef)) {
      doc.addField(pair._1.key, pair._2)
    }

  }

}


