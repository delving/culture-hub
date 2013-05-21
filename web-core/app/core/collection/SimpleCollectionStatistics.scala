package core.collection

/**
 * Simple statistics about the contents of a collection
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait SimpleCollectionStatistics {

  /**
   * Gives the count of items that have a value for the given field
   *
   * @param schemaPrefix the schema prefix
   * @param field the field name
   * @return the total count of present objects
   */
  def getPresentCount(schemaPrefix: String, field: String): Int

}