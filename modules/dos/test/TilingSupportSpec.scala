import java.io.File
import libs.PTIFTiling
import models.OrganizationConfiguration
import org.apache.commons.io.FileUtils
import test.Specs2TestContext

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class TilingSupportSpec extends Specs2TestContext {

  "The Tiling utility" should {

    "replace tiles without complaining" in {

      withTestConfig { implicit configuration: OrganizationConfiguration =>

        val sourceImage = new File("modules/dos/test/resources/smallballs.tif")

        val output = new File("/tmp/output")
        output.mkdir()

        val result = PTIFTiling.createTile(new File("/tmp"), output, sourceImage)
        result must (beRight)

        val r = new File("/tmp/output/smallballs.tif")
        r.exists() must (beTrue)

        val result2 = PTIFTiling.createTile(new File("/tmp"), output, sourceImage)
        result2 must (beRight)

        r.exists() must (beTrue)

        FileUtils.deleteQuietly(r)
      }

    }
  }

}
