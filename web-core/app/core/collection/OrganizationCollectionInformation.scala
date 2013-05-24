package core.collection

/**
 * Meta-Information about an organizational collection
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

trait OrganizationCollectionInformation extends CollectionInformation {

  def getLanguage: String

  def getCountry: String

  def getProvider: String

  def getProviderUri: String

  def getDataProvider: String

  def getDataProviderUri: String

  def getRights: String

  def getType: String

}