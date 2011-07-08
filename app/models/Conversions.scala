package models

import com.mongodb.casbah.commons.conversions.MongoConversionHelper
import eu.delving.metadata.RecordDefinition
import org.bson.{BSON, Transformer}
import cake.ComponentRegistry

/**
 * Not used for the moment but we may need them later on.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */


object RegisterDelvingConversionHelpers extends DelvingSerializers with DelvingDeserializers {

  def apply() {
    super.register()
  }
}

object DeregisterDelvingConversionHelpers extends DelvingSerializers with DelvingDeserializers {
  def apply() {
    super.unregister()
  }
}

trait DelvingSerializers extends MongoConversionHelper {
  private val encodeType = classOf[RecordDefinition]
  private val transformer = new Transformer {
    def transform(p1: AnyRef): AnyRef = p1 match {
      case r: RecordDefinition => r.prefix
    }
  }

  override def register() = {
    BSON.addEncodingHook(encodeType, transformer)
    super.register()
  }

  override def unregister() = {
    BSON.removeEncodingHooks(encodeType)
    super.unregister()
  }
}

trait DelvingDeserializers extends MongoConversionHelper {
//
//  private val encodeType = classOf[String]
//  private val transformer = new Transformer {
//    log.trace("Decoding RecordDefinition")
//
//    def transform(o: AnyRef): AnyRef = o match {
//      case maybePrefix: String => {
//        try {
//          ComponentRegistry.themeHandler.buildRecordDefinition(maybePrefix, false)
//        } catch {
//          case _ => maybePrefix
//        }
//      }
//    }
//  }
//
//  override def register() {
//    log.debug("Hooking up Delving RecordDefinition deserializer")
//    BSON.addDecodingHook(encodeType, transformer)
//    super.register()
//  }
//
//  override def unregister() {
//    BSON.removeDecodingHooks(encodeType)
//  }

}
