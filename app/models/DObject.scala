package models

import org.bson.types.ObjectId
import salatContext._
import org.apache.solr.common.SolrInputDocument
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.SalatDAO
import controllers.dos.StoredFile
import java.util.Date
import util.Constants._

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

case class DObject(_id: ObjectId = new ObjectId,
                   TS_update: Date,
                   user_id: ObjectId,
                   userName: String,
                   name: String,
                   description: String,
                   visibility: Visibility,
                   deleted: Boolean = false,
                   thumbnail_id: Option[ObjectId],
                   links: List[EmbeddedLink] = List.empty[EmbeddedLink],
                   thumbnail_file_id: Option[ObjectId] = None, // pointer to the file selected as the thumbnail. This is _not_ helping to fetch the thumbnail, which is retrieved using the ID of the object
                   files: Seq[StoredFile] = Seq.empty[StoredFile],
                   collections: List[ObjectId] = List.empty[ObjectId]) extends Thing {

  def toSolrDocument: SolrInputDocument = {
    val doc = getAsSolrDocument
    doc addField ("delving_recordType", OBJECT)
    doc
  }

  def fileIds = files.map(_.id)

}

object DObject extends SalatDAO[DObject, ObjectId](objectsCollection) with Commons[DObject] with Resolver[DObject] with Pager[DObject] {

  def findAllWithCollection(id: ObjectId) = find(MongoDBObject("collections" -> id, "deleted" -> false))

  def findAllUnassignedForUser(id: ObjectId) = find(MongoDBObject("deleted" -> false, "user_id" -> id, "collections" -> MongoDBObject("$size" -> 0)))

  def updateThumbnail(id: ObjectId, thumbnail_id: ObjectId) {
    update(MongoDBObject("_id" -> id), $set ("thumbnail_file_id" -> thumbnail_id, "thumbnail_id" -> id) , false, false)
  }

  def fetchName(id: String): String = fetchName(id, objectsCollection)

  def removeFile(oid: ObjectId) {
    DObject.update(MongoDBObject(), (MongoDBObject("$pull" -> MongoDBObject("files" -> MongoDBObject("id" -> oid)))))
  }

  def findForCollection(collectionId: ObjectId) = {
    objectsCollection.find(MongoDBObject("collections" -> collectionId, "deleted" -> false), MongoDBObject("_id" -> 1)).map(dbo => dbo.get("_id").asInstanceOf[ObjectId]).toList
  }

  def unlinkCollection(collectionId: ObjectId) {
    DObject.update(MongoDBObject("collections" -> collectionId), $pull ("collections" -> collectionId))
  }

}