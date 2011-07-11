package cake

import util.ThemeHandlerComponent
import play.scalasupport.PkBinder
import extensions.ScalaListTypeBinder


/**
 * This object uses the Cake pattern to manage Dependency Injection, see also
 * http://jonasboner.com/2008/10/06/real-world-scala-dependency-injection-di.html
 *
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

  val metadataModel: MetadataModelImpl
}

trait MetaRepoComponent {

  import eu.delving.sip.AccessKey

  val accessKey: AccessKey
//  val metaRepo: MetaRepo
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
object ComponentRegistry extends MetadataModelComponent with ThemeHandlerComponent with MetaRepoComponent
   {

  import scala.collection.JavaConversions._
  import play.Play
  import eu.delving.sip.AccessKey
  import eu.delving.metadata.MetadataModelImpl

  val metadataModel: MetadataModelImpl = new MetadataModelImpl
  metadataModel.setDefaultPrefix(Play.configuration.getProperty("services.harvindexing.prefix"))
  metadataModel.setRecordDefinitionResources(List("/abm-record-definition.xml", "/icn-record-definition.xml", "/ese-record-definition.xml"))

  val accessKey = new AccessKey
  accessKey.setServicesPassword(Play.configuration.getProperty("services.password").trim)

//  val metaRepo = new MetaRepoImpl
//  metaRepo.setResponseListSize(Play.configuration.getProperty("services.pmh.responseListSize").trim)
//  metaRepo.setHarvestStepSecondsToLive(180)
  
  val themeHandler: ThemeHandler = new ThemeHandler
  themeHandler.startup()

  play.data.binding.Binder.register(classOf[scala.collection.immutable.List[String]], new ScalaListTypeBinder)


}


//// =======================
//val warmer = ComponentRegistry.warmer
//warmer.trigger

