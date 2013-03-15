package models

import com.novus.salat
import org.bson.types.ObjectId
import salat.dao.{ SalatMongoCursor, SalatDAO }

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Pager[A <: salat.CaseClass] { self: AnyRef with SalatDAO[A, ObjectId] =>

  /**
   * Returns a page and the total object count
   * @param page the page number
   * @param pageSize optional size of the page, defaults to PAGE_SIZE
   */
  implicit def listWithPage(list: List[A]) = new {
    def page(page: Int, pageSize: Int) = {
      val p = if (page == 0) 1 else page
      val c = list.slice((p - 1) * pageSize, (p - 1) * pageSize + pageSize)
      (c, list.size)
    }
  }

  implicit def cursorWithPage(cursor: SalatMongoCursor[A]) = new {

    /**
     * Returns a page and the total object count
     * @param page the page number
     * @param pageSize optional size of the page, defaults to PAGE_SIZE
     */
    def page(page: Int, pageSize: Int) = {
      val p = if (page == 0) 1 else page
      val c = cursor.skip((p - 1) * pageSize).limit(pageSize)
      (c.toList, c.count)
    }
  }
}