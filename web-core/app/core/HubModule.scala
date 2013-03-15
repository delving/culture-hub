package core

import _root_.core.node.{ NodeDirectoryService, NodeSubscriptionService, NodeRegistrationService }
import core.schema.SchemaProvider
import _root_.core.services.{ AggregatingNodeSubscriptionService, AggregatingOrganizationCollectionLookupService, AggregatingHarvestCollectionLookup }
import com.escalatesoft.subcut.inject.NewBindingModule

/**
 * Experimenting with DI
 */
object HubModule extends NewBindingModule({ module =>

  import module._

  bind[SchemaService].toSingle(new SchemaProvider)

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