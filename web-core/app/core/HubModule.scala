package core

import core.schema.SchemaProvider
import core.services.{AggregatingOrganizationCollectionLookupService, AggregatingHarvestCollectionLookup}
import org.scala_tools.subcut.inject.NewBindingModule

/**
 * Experimenting with DI
 */
object HubModule extends NewBindingModule({ module =>

  import module._

  bind [SchemaService].toSingle ( new SchemaProvider )

  bind [HarvestCollectionLookupService].toSingle ( new AggregatingHarvestCollectionLookup )

  bind [OrganizationCollectionLookupService].toSingle ( new AggregatingOrganizationCollectionLookupService )

  bind [DomainServiceLocator[AuthenticationService]].toSingleInstance(HubServices.authenticationServiceLocator)
  bind [DomainServiceLocator[RegistrationService]].toSingleInstance(HubServices.registrationServiceLocator)
  bind [DomainServiceLocator[UserProfileService]].toSingleInstance(HubServices.userProfileServiceLocator)
  bind [DomainServiceLocator[OrganizationService]].toSingleInstance(HubServices.organizationServiceLocator)
  bind [DomainServiceLocator[DirectoryService]].toSingleInstance(HubServices.directoryServiceLocator)

})