package core

import core.schema.SchemaProvider
import org.scala_tools.subcut.inject.NewBindingModule

/**
 * Experimenting with DI
 */
object HubModule extends NewBindingModule({ module =>

  import module._

  bind [SchemaService].toSingle ( new SchemaProvider )

})