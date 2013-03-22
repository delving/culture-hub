package search

import _root_.org.scalatest.matchers.ShouldMatchers
import core.search.{ PageLink, Pager }
import org.scalatest.FunSpec
import scala.collection.JavaConversions._
import java.util.List

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since Apr 5, 2010 10:55:58 PM
 */

class ResultPaginationSpec extends FunSpec with ShouldMatchers {

  describe("A SOLRResultPagination") {

    describe("(when a start pagination range)") {
      testPagination(300, 20, 0, "simple query")
    }

    describe("(when given a middle pagination range)") {
      testPagination(300, 20, 140, "simple query")
    }

    describe("(when given a near end pagination range)") {
      testPagination(300, 20, 260, "simple query")
    }

    describe("(when given an end pagination range)") {
      testPagination(300, 20, 280, "simple query")
    }

    describe("(when given a non inclusive end pagination range)") {
      testPagination(295, 20, 280, "simple query")
    }
  }

  def testPagination(numFound: Int, rows: Int, start: Int, query: String): Unit = {
    val pager: Pager = Pager(numFound, start, rows)

    it("should not have a previous page if start is less then number of rows") {
      if (rows > start)
        pager.hasPreviousPage should be(false)
      else
        pager.hasPreviousPage should be(true)

    }

    it("should have a next page when start + rows is smaller then numFound") {
      if ((start + rows) >= numFound)
        pager.hasNextPage should be(false)
      else
        pager.hasNextPage should be(true)
    }

    it("should start should be start") {
      pager.start should equal(start)
    }

    it("should nextPage should be start + rows") {
      pager.nextPageNumber should equal(start + rows)
    }

    it("should give back numFound unmodified") {
      pager.numFound should equal(numFound)
    }

    it("should give back rows unmodified") {
      pager.rows should equal(rows)
    }

    it("should have pageNumber that is start divided by rows + 1") {
      pager.currentPageNumber should equal(start / rows + 1)
    }

    // TODO: Sjoerd, FIXME!
    //    it("should give back number of rows + start as last viewable record") {
    //      val lastViewableRecord = if (start + rows > numFound) numFound else start + rows
    //      pager.lastViewableRecord should equal(lastViewableRecord)
    //    }

    it("should give back 10 pagelinks when numfound exceeds 10 times the number of rows") {
      if (numFound > (10 * rows))
        pager.pageLinks.size should equal(10)
    }

    it("should give back numFound divided by rows with numFound is less then 10 times the number of rows") {
      if (numFound < (10 * rows))
        pager.pageLinks.size should equal(numFound / rows)
    }

    it("should have only the first pageLink marked as not linked") {
      val notLinkedPageLink = pager.pageLinks.filter(_.isLinked == false)
      notLinkedPageLink.size should equal(1)
      notLinkedPageLink.head.display should equal(start / rows + 1)
    }

    it("should have each pagelink, that is linked, as start the display value - 1 * number for rows") {
      val pageLinks: List[PageLink] = pager.pageLinks
      pageLinks.filter(_.isLinked == true).forall(pl => ((pl.display - 1) * rows + 1) == pl.start) should be(true)
    }
  }
}
