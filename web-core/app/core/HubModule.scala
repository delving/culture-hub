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

})