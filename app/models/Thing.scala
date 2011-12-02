package models

import org.apache.solr.common.SolrInputDocument
import com.novus.salat.annotations.raw.Salat
import org.bson.types.ObjectId
import java.util.Date
import util.Constants._

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
    doc addField (ID, _id)
    doc addField (HUB_ID, "%s_%s_%s".format(userName, getType, _id))
    doc addField (RECORD_TYPE, getType)
    doc addField (IDX_VISIBILITY, visibility.value.toString)
    doc addField (IDX_OWNER, userName)
    doc addField (IDX_CREATOR, userName)
    doc addField ("europeana_provider_single", userName) // TODO remove?
    doc addField (IDX_DESCRIPTION, description)
    doc addField (IDX_TITLE, name)
    if (thumbnail_id != None) {
      doc addField(IDX_THUMBNAIL, thumbnail_id.get)
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