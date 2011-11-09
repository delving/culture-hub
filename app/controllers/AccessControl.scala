package controllers

import org.bson.types.ObjectId
import models.{Group, User}
import models.salatContext._
import com.mongodb.casbah.Imports._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object AccessControl {

  val ORGANIZATIONS = "organizations"
  val GROUPS = "groups"

}