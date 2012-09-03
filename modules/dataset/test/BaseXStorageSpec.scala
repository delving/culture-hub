import models.DataSet
import org.specs2.mutable.Specification
import util.DomainConfigurationHandler
import play.api.test._
import play.api.test.Helpers._

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class BaseXStorageSpec extends Specs2TestContext {

  "the BaseX storage" should {

    "properly insert documents with &amp;" in {

      withTestData {
        // data is loaded at bootstrap time of the DataSet plugin
        implicit val configuration = DomainConfigurationHandler.getByOrgId("delving")
        val dataSet = DataSet.dao.findBySpecAndOrgId("PrincessehofSample", "delving").get
        DataSet.dao.getSourceRecordCount(dataSet) must equalTo(8)
      }

    }
  }

}
