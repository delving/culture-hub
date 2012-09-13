package core

import models.{MongoMetadataCache, MetadataItem}
import java.util.Date


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait MetadataCache {

  def underlying: MongoMetadataCache

  def saveOrUpdate(item: MetadataItem)

  def iterate(index: Int = 0, limit: Option[Int] = None, from: Option[Date] = None, until: Option[Date] = None): Iterator[MetadataItem]

  def list(index: Int = 0, limit: Option[Int] = None, from: Option[Date] = None, until: Option[Date] = None): List[MetadataItem]

  def findOne(itemId: String): Option[MetadataItem]

  def findMany(itemIds: Seq[String]): Seq[MetadataItem]

  def count(): Long

  def remove(itemId: String)

  def removeAll()

}
