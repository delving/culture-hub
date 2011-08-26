package models

import com.novus.salat.annotations.raw.Salat
import org.bson.types.ObjectId

/**
 * Abstract repository / collection
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

@Salat
trait Repository {
  val _id: ObjectId
  val name: String
  val node: String
  val description: Option[String]
  // val organization: String
  // val organizationNode: String
//  val access: AccessRight
}