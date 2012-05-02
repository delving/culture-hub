package core

import models.MetadataItem

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait MetadataCache {

  def saveOrUpdate(item: MetadataItem)

}
