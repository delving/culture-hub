import controllers.SipCreatorEndPoint
import core.processing.DataSetCollectionProcessor
import java.io.{File, FileInputStream}
import java.util.zip.GZIPInputStream
import models.{DataSetState, DataSet}
import org.specs2.mutable.Specification

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */

class BaseXStorageSpec extends Specification with TestContext {

  "the BaseX storage" should {

    "properly insert documents with &amp;" in {

      withTestData {

        val dataSet = DataSet.findBySpecAndOrgId("PrincessehofSample", "delving").get
        SipCreatorEndPoint.loadSourceData(dataSet, new GZIPInputStream(new FileInputStream(new File("conf/bootstrap/EA525DF3C26F760A1D744B7A63C67247__source.xml.gz"))))
        DataSet.updateState(dataSet, DataSetState.QUEUED)
        DataSetCollectionProcessor.process(dataSet)

        1 must equalTo(1)

        // now we wait since the parsing is asynchronous
        Thread.sleep(3000)

        DataSet.getRecordCount(dataSet) must equalTo(8)

      }



    }
  }

}
