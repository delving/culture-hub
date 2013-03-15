package util

import models.OrganizationConfiguration
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import play.api.Logger
import reflect.ClassTag

/**
 * Holds a resource that is dependent on the OrganizationConfiguration
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
abstract class OrganizationConfigurationResourceHolder[A, B](val name: String) {

  val log = Logger("CultureHub")

  private var configuredFor = Seq.empty[OrganizationConfiguration]

  private val resources = new ConcurrentHashMap[OrganizationConfiguration, B]()

  private[util] def configure(configurations: Seq[OrganizationConfiguration]) {
    val existing: Seq[OrganizationConfiguration] = resources.keys().asScala.toSeq
    val intersection = existing.intersect(configurations)
    val added = configurations.filter(!intersection.contains(_))
    val removed = existing.filter(!intersection.contains(_))
    val modified = intersection.filter { element =>

      // remember, equality of the OrganizationConfiguration is computed solely on the orgId!
      // hence we retrieve the original and updated elements here, and do a better analysis
      val original = existing.find(o => element.orgId == o.orgId)
      val updated = configurations.find(o => element.orgId == o.orgId)

      if (original.isDefined && updated.isDefined) {
        resourceConfiguration(original.get) != resourceConfiguration(updated.get)
      } else {
        true
      }
    }

    def addResource(configuration: OrganizationConfiguration) {
      val rConfig = resourceConfiguration(configuration)
      log.trace(s"${getClass.getName} About to add new resource of kind $name for configuration ${configuration.orgId}")
      val r = onAdd(rConfig)
      if (r == None) {
        log.error(
          s"${this.getClass.getName} Could not initialize resource of kind $name for organization ${configuration.orgId}"
        )
      } else {
        log.trace(s"Adding resource of kind $name for configuration ${configuration.orgId}")
        resources.put(configuration, r.get)
      }
    }

    added foreach { a => addResource(a) }

    removed foreach { r => onRemove(resources.get(r)) }

    modified foreach { m =>
      onRemove(resources.get(m))
      resources.remove(m)

      addResource(m)
    }

    configuredFor = configurations

  }

  // ~~~ to implement

  /**
   * Extracts the configuration value required for the holder to initialize itself
   * @param configuration the [[ models.OrganizationConfiguration ]] used for the initialization
   * @return the configuration value required for initialization of a single resource, e.g. an URL or server IP
   */
  protected def resourceConfiguration(configuration: OrganizationConfiguration): A

  /**
   * Computes a resource when a new configuration is added.
   * Error handling should be handled by this method
   * @param resourceConfiguration the configuration required for the resource to be initialized
   * @return the configured resource
   */
  protected def onAdd(resourceConfiguration: A): Option[B]

  /**
   * Performs cleanup operations when a configuration is removed
   * @param removed the resource to be removed
   */
  protected def onRemove(removed: B)

  // ~~~ public interface

  /**
   * Gets a resource for the given configuration
   *
   * @param configuration the [[ models.OrganizationConfiguration ]] for which to retrieve the resource
   * @return a configured Resource
   */
  def getResource(configuration: OrganizationConfiguration)(implicit ct: ClassTag[B]): B = {
    val maybeResource = Option(resources.get(configuration))
    if (maybeResource == None) {
      log.error(s"Could not retrieve resource of kind $name for organization ${configuration.orgId}. This holder is configured for ${configuredFor.map(_.orgId).mkString(", ")}")
      throw new RuntimeException(s"Resource of kind $name unavailable for organizations ${configuration.orgId}")
    } else {
      resources.get(configuration)
    }
  }

  def findResource(p: ((OrganizationConfiguration, B)) => Boolean) = resources.asScala.find(p)

  def allConfigurations: Seq[OrganizationConfiguration] = resources.asScala.keys.toSeq

  def allResources: Seq[B] = resources.asScala.values.toSeq

}
