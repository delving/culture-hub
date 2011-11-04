package models

import com.novus.salat.annotations.raw.Salat
import org.bson.types.ObjectId
import java.util.Date

/**
 * A Thing
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

@Salat
trait Thing {
  val _id: ObjectId
  val TS_update: Date
  val user_id: ObjectId
  val username: String
  val name: String
  val description: String
  val visibility: Visibility
  val thumbnail_id: Option[ObjectId]
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