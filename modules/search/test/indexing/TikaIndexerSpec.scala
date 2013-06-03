package indexing

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSpec
import services.TikaIndexer
import scala.concurrent.Await
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 12/20/11 11:58 AM
 */

class TikaIndexerSpec extends FunSpec with ShouldMatchers {

  describe("A TikaIndexer") {

    describe("(when receiving an url to a pdf object)") {

      it("should retrieve and index it") {
        val output: Option[String] = Await.result(TikaIndexer.getFullTextFromRemoteURL("http://test.hops-research.org/all/brabants_heem_1948_1_1_1_(2).pdf"), 10 seconds)
        output.get.isEmpty should be(false)
        println(output.get)
      }

    }

    describe("(when receiving a bad url)") {

      it("should give back None") {
        Await.result(TikaIndexer.getFullTextFromRemoteURL("http://test.hops-research.org/all/brabants_heem_1900_1_1_1_(2).pdf"), 10 seconds) should be(None)
      }

    }

  }

}