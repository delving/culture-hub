package core

import core.node.{ NodeDirectoryService, NodeSubscriptionService, NodeRegistrationService }
import core.schema.SchemaProvider
import core.services.{ AggregatingNodeSubscriptionService, AggregatingOrganizationCollectionLookupService, AggregatingHarvestCollectionLookup }
import com.escalatesoft.subcut.inject.NewBindingModule
import search.SearchService

/**
 * Experimenting with DI
 */
object HubModule extends NewBindingModule({ module =>

  import module._

  bind[SchemaService].toSingle(new SchemaProvider)

  bind[DomainServiceLocator[SearchService]].toSingle(HubServices.searchServiceLocator)

  bind[HarvestCollectionLookupService].toSingle(new AggregatingHarvestCollectionLookup)

  bind[OrganizationCollectionLookupService].toSingle(new AggregatingOrganizationCollectionLookupService)

  bind[NodeSubscriptionService].toSingle(new AggregatingNodeSubscriptionService)

  bind[DomainServiceLocator[AuthenticationService]].toSingleInstance(HubServices.authenticationServiceLocator)
  bind[DomainServiceLocator[RegistrationService]].toSingleInstance(HubServices.registrationServiceLocator)
  bind[DomainServiceLocator[UserProfileService]].toSingleInstance(HubServices.userProfileServiceLocator)
  bind[DomainServiceLocator[OrganizationService]].toSingleInstance(HubServices.organizationServiceLocator)
  bind[DomainServiceLocator[DirectoryService]].toSingleInstance(HubServices.directoryServiceLocator)
  bind[DomainServiceLocator[NodeRegistrationService]].toSingleInstance(HubServices.nodeRegistrationServiceLocator)
  bind[DomainServiceLocator[NodeDirectoryService]].toSingleInstance(HubServices.nodeDirectoryServiceLocator)

})