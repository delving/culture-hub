package core

import models.{ MongoMetadataCache, MetadataItem }
import java.util.Date

/**
 * A generic storage API for metadata items. We call it a cache because long-term persistent storage should be carried
 * out by another layer, more adapted to that purpose.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait MetadataCache {

  def underlying: MongoMetadataCache

  /**
   * Saves or updates a cache item
   * @param item the [[models.MetadataItem]] to store
   */
  def saveOrUpdate(item: MetadataItem)

  /**
   * Iterates over records given specific criteria
   *
   * @param index the beginning index
   * @param limit the maximum amount of items to iterate over
   * @param from an optional beginning date
   * @param until an optional end date
   * @return an iterator of [[models.MetadataItem]]
   */
  def iterate(index: Int = 0, limit: Option[Int] = None, from: Option[Date] = None, until: Option[Date] = None): Iterator[MetadataItem]

  /**
   * Lists records given specific criteria
   *
   * @param index the beginning index
   * @param limit the maximum amount of items to iterate over
   * @param from an optional beginning date
   * @param until an optional end date
   *
   * @return a List of [[models.MetadataItem]]
   */
  def list(index: Int = 0, limit: Option[Int] = None, from: Option[Date] = None, until: Option[Date] = None): List[MetadataItem]

  /**
   * Finds an item given one ID
   * @param itemId the ID of the item to retrieve
   * @return a [[models.MetadataItem]] if found
   */
  def findOne(itemId: String): Option[MetadataItem]

  /**
   * Finds many items given a sequence of IDs
   * @param itemIds the ids of the items to retrieve
   * @return a sequence of [[models.MetadataItem]]
   */
  def findMany(itemIds: Seq[String]): Seq[MetadataItem]

  /**
   * The amount of items in this cache
   * @return the count of items
   */
  def count(): Long

  /**
   * Returns one item given its ID
   * @param itemId the item ID
   */
  def remove(itemId: String)

  /**
   * Clears the cache
   */
  def removeAll()

}