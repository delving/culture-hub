package controllers

import org.bson.types.ObjectId
import views.Thing._
import models.{MetadataRecord, DataSet}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.Implicits._
import models.salatContext._
import com.mongodb.casbah.MongoCollection
import com.novus.salat.dao.SalatMongoCursor

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

object Things extends DelvingController {

  import views.context.PAGE_SIZE

  implicit def addPager(cursor: MongoCollection#CursorType) = new {

    def page(page: Int, pageSize: Int = PAGE_SIZE) = {
      val p = if (page == 0) 1 else page
      val c = cursor.skip((p - 1) * pageSize).limit(PAGE_SIZE)
      (c.toList, c.count)
    }
  }

  implicit def cursorWithPage(cursor: SalatMongoCursor[MetadataRecord]) = new {

    /**
     * Returns a page and the total object count
     * @param page the page number
     * @param pageSize optional size of the page, defaults to PAGE_SIZE
     */
    def page(page: Int, pageSize: Int = PAGE_SIZE) = {
      val p = if (page == 0) 1 else page
      val c = cursor.skip((p - 1) * pageSize).limit(PAGE_SIZE)
      (c.toList, c.count)
    }
  }


  def list(spec: String, page: Int = 1): AnyRef = {

    val dataSet: DataSet = DataSet.findBySpec(spec).getOrElse(return NotFound)

    val records: SalatMongoCursor[MetadataRecord] = DataSet.getRecords(dataSet).find(MongoDBObject())
    val pages = records.page(page)

    val count = pages._2
    val items = pages._1.map(r => Thing(r._id, "Here comes the title", r.rawMetadata))

    // TODO here we need to map to the output format selected through the theme or selected by the user

    html.list(title = "All things in " + spec, spec = spec, things = items, page = page, count = count)
  }
}

case class Thing(id: ObjectId, title: String, stuff: Map[_, _] = Map.empty[String, String])