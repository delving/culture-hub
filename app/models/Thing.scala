package models

import org.apache.solr.common.SolrInputDocument
import com.novus.salat.annotations.raw.Salat
import org.bson.types.ObjectId
import java.util.Date

@Salat
trait Base extends AnyRef with Product {

  val _id: ObjectId
  val TS_update: Date
  val user_id: ObjectId
  val userName: String

}


/**
 * A Thing
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

@Salat
trait Thing extends Base {

  val name: String
  val description: String
  val visibility: Visibility
  val deleted: Boolean
  val thumbnail_id: Option[ObjectId]
  val links: List[EmbeddedLink]

  def freeTextLinks = links.filter(_.linkType == Link.LinkType.FREETEXT)
  def placeLinks = links.filter(_.linkType == Link.LinkType.PLACE)

  def getType: String
  def toSolrDocument: SolrInputDocument = getAsSolrDocument

  protected def getAsSolrDocument: SolrInputDocument = {
    val doc = new SolrInputDocument
    doc addField ("id", _id)
    doc addField ("delving_recordType", getType)
    doc addField ("delving_visibility_single", visibility.value.toString) // TODO give accurate type, this is an integer
    doc addField ("delving_user_id_single", user_id)
    doc addField ("delving_userName_single", userName)
    doc addField ("europeana_provider_single", userName) // TODO remove
    doc addField ("delving_description_text", description)
    doc addField ("delving_name_text", name)
    if (thumbnail_id != None) {
      doc addField("delving_thumbnail_id_single", thumbnail_id.get)
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