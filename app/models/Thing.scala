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

  protected def getAsSolrDocument: SolrInputDocument = {
    val doc = new SolrInputDocument
    doc addField ("id", _id)
    doc addField ("delving_user_id_single", user_id)
    doc addField ("delving_userName_single", userName)
    doc addField ("delving_description_text", description)
    doc addField ("delving_name_text", name)
    if (thumbnail_id != None) {
      doc addField("delving_thumbnail_id_string", thumbnail_id.get)
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