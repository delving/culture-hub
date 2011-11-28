package models

import org.bson.types.ObjectId
import com.novus.salat.dao.SalatDAO
import salatContext._
import java.util.Date
import com.mongodb.casbah.Imports._
import org.apache.solr.common.SolrInputDocument
import util.Constants._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class UserCollection(_id: ObjectId = new ObjectId,
                          TS_update: Date,
                          user_id: ObjectId,
                          userName: String,
                          name: String,
                          description: String,
                          visibility: Visibility,
                          deleted: Boolean = false,
                          thumbnail_id: Option[ObjectId],
                          links: List[EmbeddedLink] = List.empty[EmbeddedLink]) extends Thing {

  def toSolrDocument: SolrInputDocument = {
    val doc = getAsSolrDocument
    doc addField("delving_recordType", USERCOLLECTION)
    doc
  }
}

object UserCollection extends SalatDAO[UserCollection, ObjectId](userCollectionsCollection) with Commons[UserCollection] with Resolver[UserCollection] with Pager[UserCollection] {

  def fetchName(id: String): String = fetchName(id, userCollectionsCollection)

  def setObjects(id: ObjectId, objectIds: List[ObjectId]) {
    userCollectionsCollection.update(MongoDBObject("_id" -> id), $set("linkedObjects" -> objectIds))
  }

}