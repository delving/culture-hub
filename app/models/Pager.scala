package models

import com.novus.salat
import org.bson.types.ObjectId
import salat.dao.{SalatMongoCursor, SalatDAO}

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Pager[A <: salat.CaseClass] { self: AnyRef with SalatDAO[A, ObjectId] =>

  implicit def cursorWithPage(cursor: SalatMongoCursor[DObject]) = new {

    /**
     * Returns a page and the total page count
     * @param page the page number
     * @param pageSize optional size of the page, defaults to 20
     */
    def page(page: Int, pageSize: Int = 20) = {
      val c = cursor.skip((page - 1) * pageSize).limit(20)
      (c.toList, c.count)
    }
  }
}