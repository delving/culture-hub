import core.indexing.SOLRIndexingService
import test.Specs2TestContext

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 12/24/12 3:22 PM
 */
class IndexingServiceSpec extends Specs2TestContext {

  "The coordinate checker" should {

    "let valid coordinates through" in {
      val test = List("53.084797,4.874389", "53.084797,4.874389")
      SOLRIndexingService.filterForValidGeoCoordinate(test).size must equalTo(2)
    }

    "reject all invalid coordinates" in {
      val test = List("sjoerd", "53.084797,4.874389")
      SOLRIndexingService.filterForValidGeoCoordinate(test).size must equalTo(1)
    }

    "remove spaces from the coordinates" in {
      val test = List("53.084797, 4.874389")
      SOLRIndexingService.filterForValidGeoCoordinate(test).head must equalTo("53.084797,4.874389")
    }

  }
}

