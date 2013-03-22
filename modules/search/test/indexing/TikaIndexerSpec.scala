package indexing

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSpec
import services.TikaIndexer

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 12/20/11 11:58 AM
 */

class TikaIndexerSpec extends FunSpec with ShouldMatchers {

  describe("A TikaIndexer") {

    describe("(when receiving an url to a pdf object)") {

      it("should retrieve and index it") {
        val output: Option[String] = TikaIndexer.getFullTextFromRemoteURL("http://test.hops-research.org/all/brabants_heem_1948_1_1_1_(2).pdf")
        output.get.isEmpty should be(false)
        println(output.get)
      }

    }

    describe("(when receiving a bad url)") {

      it("should give back None") {
        TikaIndexer.getFullTextFromRemoteURL("http://test.hops-research.org/all/brabants_heem_1900_1_1_1_(2).pdf") should be(None)
      }

    }

  }

}