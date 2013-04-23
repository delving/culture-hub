package services

import core.{ ItemType, OrganizationCollectionLookupService }
import models.OrganizationConfiguration
import core.collection.OrganizationCollection
import plugins.SimpleDocumentUploadPlugin

/**
 * Lookup for the collections provided by this plugin
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class UploadDocumentOrganizationCollectionLookupService extends OrganizationCollectionLookupService {

  def findAll(implicit configuration: OrganizationConfiguration): Seq[OrganizationCollection] = Seq(

    new OrganizationCollection {

      def getCreator: String = "system" // until this plugin allows to create collections, we use this

      def getOwner: String = configuration.orgId

      val itemType: ItemType = SimpleDocumentUploadPlugin.ITEM_TYPE

      val spec: String = SimpleDocumentUploadPlugin.pluginConfiguration.collectionName
    }
  )

  def findBySpecAndOrgId(spec: String, orgId: String)(implicit configuration: OrganizationConfiguration): Option[OrganizationCollection] = findAll.headOption
}
