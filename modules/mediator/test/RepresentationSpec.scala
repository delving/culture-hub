import controllers.mediator.Representations
import core.HubModule
import java.io.File
import models.OrganizationConfiguration
import org.apache.commons.io.FileUtils
import play.api.test._
import play.api.test.Helpers._
import plugins.MediatorPlugin
import test.Specs2TestContext
import util.OrganizationConfigurationHandler

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class RepresentationSpec extends Specs2TestContext {

  "The representation controller" should {

    val representationController = new Representations()(HubModule)

    "find a resource on disk" in new WithApplication {

      implicit val configuration = OrganizationConfigurationHandler.getByOrgId("delving")

      val p = new File(MediatorPlugin.pluginConfiguration.archiveDirectory, "delving/testCollection")
      p.mkdirs()
      val f = new File(p, "testIdentifier.jpg")
      f.createNewFile()

      representationController.findResourceFile("delving", "testCollection", "testIdentifier") should equalTo(Some(f))

      FileUtils.deleteQuietly(p)
    }

    "render a resource" in {
      val here = new File("modules/mediator/test/resources")
      withTestConfig(Map("configurations.delving.plugin.mediator.archiveDirectory" -> here.getAbsolutePath)) { implicit configuration: OrganizationConfiguration =>

        val request = representationController.representation("image", "delving", "testSpec", "logo")(FakeRequest())

        status(request) must equalTo(OK)
      }
    }

    val here = new File("modules/mediator/test/resources")
    val accessKeyConfig = Map(
      "configurations.delving.plugin.mediator.archiveDirectory" -> here.getAbsolutePath,
      "configurations.delving.plugin.mediator.sourceImageRepresentationAccessKey" -> "foobar"
    )

    "refuse to render a protected resources without accessKey" in {
      withTestConfig(accessKeyConfig) { implicit configuration: OrganizationConfiguration =>
        val request = representationController.representation("image", "delving", "testSpec", "logo")(FakeRequest())
        status(request) must equalTo(UNAUTHORIZED)
      }
    }

    "refuse to render a protected resources given a wrong accessKey" in {
      withTestConfig(accessKeyConfig) { implicit configuration: OrganizationConfiguration =>
        val request = representationController.representation("image", "delving", "testSpec", "logo", Some("barfoo"))(FakeRequest())
        status(request) must equalTo(UNAUTHORIZED)
      }
    }

    "render a protected resource given the correct accessKey" in {
      withTestConfig(accessKeyConfig) { implicit configuration: OrganizationConfiguration =>
        val request = representationController.representation("image", "delving", "testSpec", "logo", Some("foobar"))(FakeRequest())
        status(request) must equalTo(OK)
      }
    }
  }

}
