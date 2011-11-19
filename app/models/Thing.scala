package models

import com.novus.salat.annotations.raw.Salat
import org.bson.types.ObjectId
import java.util.Date

/**
 * A Thing
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

@Salat
trait Thing extends AnyRef with Product {

  import org.apache.solr.common.SolrInputDocument

  val _id: ObjectId
  val TS_update: Date
  val user_id: ObjectId
  val userName: String
  val name: String
  val description: String
  val visibility: Visibility
  val deleted: Boolean
  val thumbnail_id: Option[ObjectId]

  def getAsSolrDocument: SolrInputDocument = {
    val doc = new SolrInputDocument
    doc setField ("id", _id)
    doc setField ("delving_user_id_single", user_id)
    doc setField ("delving_userName_single", userName)
    doc setField ("delving_description_text", description)
    doc setField ("delving_name_text", name)
    if (thumbnail_id.get != None) {
      doc setField("delving_thumbnail_id_string", thumbnail_id.get)
    }
    doc
  }


}


case class Visibility(value: Int)
object Visibility {
  val PRIVATE = Visibility(0)
  val PUBLIC = Visibility(10)
  val values: Map[Int, String] = Map(PRIVATE.value -> "private", PUBLIC.value -> "public")
  def name(value: Int): String = values.get(value).getOrElse(throw new IllegalArgumentException("Illegal value %s for Visibility".format(value)))
  def get(value: Int) = {
    if(!values.contains(value)) throw new IllegalArgumentException("Illegal value %s for Visibility".format(value))
    Visibility(value)
  }
}