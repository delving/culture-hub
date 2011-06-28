package eu.delving.lib

import java.io.File
import net.liftweb.util.Props

/**
 * Trait that holds various utility methods
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait Util {

  /**
   * Gets a path from the file system, based on a Props key. If the key or path is not found, an exception is thrown.
   */
  def getPath(key: String): File = {
    val imageStorePath = Props.get(key)
    if (imageStorePath.isEmpty) {
      throw new RuntimeException("You need to configure %s in property file with mode %s" format (key, Props.modeName))
    }
    val imageStore = new File(imageStorePath.openTheBox)
    if (!imageStore.exists()) {
      throw new RuntimeException("Could not find path %s for key %s" format (imageStore.getAbsolutePath, key))
    }
    imageStore
  }


}