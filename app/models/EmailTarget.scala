package models

import com.novus.salat.dao.SalatDAO
import com.mongodb.casbah.commons.TypeImports._
import com.mongodb.casbah.MongoConnection
import models.salatContext._

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
case class EmailTarget(adminTo: String = "test-user@delving.eu",
                       exceptionTo: String = "test-user@delving.eu",
                       feedbackTo: String = "test-user@delving.eu",
                       registerTo: String = "test-user@delving.eu",
                       systemFrom: String = "noreply@delving.eu",
                       feedbackFrom: String = "noreply@delving.eu")

object EmailTarget extends SalatDAO[EmailTarget, ObjectId](collection = emailTargetCollection)
