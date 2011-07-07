package cake

import util.ThemeHandlerComponent


/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/6/11 4:36 PM  
 */

// =======================
// service interfaces
//trait OnOffDeviceComponent {
//  val onOff: OnOffDevice
//  trait OnOffDevice {
//    def on: Unit
//    def off: Unit
//  }
//}
//
//trait SensorDeviceComponent {
//  val sensor: SensorDevice
//  trait SensorDevice {
//    def isCoffeePresent: Boolean
//  }
//}

trait MetadataModelComponent {

  import eu.delving.metadata.MetadataModelImpl
  val metadataModel : MetadataModelImpl

}

// =======================
// service implementations
//trait OnOffDeviceComponentImpl extends OnOffDeviceComponent {
//  class Heater extends OnOffDevice {
//    def on = println("heater.on")
//    def off = println("heater.off")
//  }
//}
//trait SensorDeviceComponentImpl extends SensorDeviceComponent {
//  class PotSensor extends SensorDevice {
//    def isCoffeePresent = true
//  }
//}
// =======================
// service declaring two dependencies that it wants injected
//trait WarmerComponentImpl {
//  this: SensorDeviceComponent with OnOffDeviceComponent =>
//  class Warmer {
//    def trigger = {
//      if (sensor.isCoffeePresent) onOff.on
//      else onOff.off
//    }
//  }
//}


// =======================
// instantiate the services in a module
object ComponentRegistry extends MetadataModelComponent with ThemeHandlerComponent
   {

  import eu.delving.metadata.MetadataModelImpl
  import scala.collection.JavaConversions._
  import play.Play

  val metadataModel: MetadataModelImpl = new MetadataModelImpl
  metadataModel.setDefaultPrefix(Play.configuration.getProperty("services.harvindexing.prefix"))
  metadataModel.setRecordDefinitionResources(List("/abm-record-definition.xml", "/icn-record-definition.xml", "/ese-record-definition.xml"))

  val themeHandler: ThemeHandler = new ThemeHandler
  themeHandler.update()
}

//// =======================
//val warmer = ComponentRegistry.warmer
//warmer.trigger

