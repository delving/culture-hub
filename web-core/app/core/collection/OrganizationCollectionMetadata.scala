package core.collection

/**
 * Meta-data about an organizational collection
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait OrganizationCollectionMetadata extends CollectionMetadata {

  /**
   * The language of the collection
   * @return the two-letter ISO code representing the language
   */
  def getLanguage: String

  /**
   * The name of the country of this collection
   * @return the name of the country
   */
  def getCountry: String

  /**
   * The name of the provider of this collection
   * @return the provider name
   */
  def getProvider: String

  /**
   * Provider URI
   * @return an URI to the provider's home page
   */
  def getProviderUri: String

  /**
   * The name of the data provider of this collection
   * @return the data provider name
   */
  def getDataProvider: String

  /**
   * Data provider URI
   * @return an URI to the data provider home page
   */
  def getDataProviderUri: String

  /**
   * The rights of this collection
   * @return the name of the rights associated with this collection
   */
  def getRights: String

  /**
   * The type of records in this collection
   * @return the type of records (image, audio, ...) of the collection
   */
  def getType: String

}