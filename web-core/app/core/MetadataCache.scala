package core

import models.{MongoMetadataCache, MetadataItem}


/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait MetadataCache {

  def underlying: MongoMetadataCache

  def saveOrUpdate(item: MetadataItem)

  def iterate(index: Int = 0, limit: Option[Int] = None): Iterator[MetadataItem]

  def list(index: Int = 0, limit: Option[Int] = None): List[MetadataItem]

  def findOne(itemId: String): Option[MetadataItem]

  def count(): Long

  def remove(itemId: String)

  def removeAll()

}
