package search

/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.net.URLEncoder
import org.apache.solr.client.solrj.SolrQuery
import collection.immutable.List
import core.search._
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSpec

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since Apr 2, 2010 12:44:15 AM
 */

class BreadCrumbSpec extends FunSpec with ShouldMatchers {

  val queryString = "single query"
  val encodedQueryString = URLEncoder.encode(queryString, "utf-8")
  val filterPrefix = "&qf="
  val queryPrefix = "query=" + encodedQueryString;

  describe("A List of BreadCrumbs") {

    describe("(when given a SolrQuery without FilterQueries)") {
      val chQuery = CHQuery(new SolrQuery(queryString))
      val list: List[BreadCrumb] = SolrQueryService.createBreadCrumbList(chQuery)

      it("should only contain the query") {
        list.size should equal(1)
      }

      val breadcrumb: BreadCrumb = list.head

      it("should have the query as the first item") {
        breadcrumb.display should equal(queryString)
        breadcrumb.href should equal(queryPrefix)
      }

      it("should have the query flagged as last") {
        breadcrumb.isLast should be(true)
      }

    }

    describe("(when given a SolrQuery with FilterQueries)") {
      val solrQuery = new SolrQuery(queryString)
      val rawFilterQueries: List[String] = List("YEAR:1900", "YEAR:1901", "LOCATION:Here")
      rawFilterQueries foreach (solrQuery addFacetField _)
      val filterQueries = SolrQueryService.createFilterQueryList(rawFilterQueries.toArray)
      val list = SolrQueryService.createBreadCrumbList(CHQuery(solrQuery = solrQuery, filterQueries = filterQueries))

      it("should contain as many entries as filterqueries + the query") {
        list.size should equal(filterQueries.length + 1)
      }

      it("should retain the same order as the FilterQueries") {
        val displayList = for (fq <- list.tail) yield fq.display
        rawFilterQueries should equal(displayList)
      }

      it("only the last entry should be flagged as true") {
        list.head.isLast should be(false)
        list.last.isLast should be(true)
        list.filter(_.isLast).size should be(1)
        list.filter(_.isLast == false).size should be(rawFilterQueries.size)
      }

      it("should format the href in same order as the FilterQueries") {
        val filterBreadCrumbList = list.tail
        for (index <- 0 until filterBreadCrumbList.length) {
          filterBreadCrumbList(index).href should equal {
            rawFilterQueries.take(index + 1).mkString(start = queryPrefix + filterPrefix, sep = filterPrefix, end = "")
          }
        }
      }

      it("should have all filterQueries marked as true for filterQueries and field + value should be non-empty") {
        list.head.field.isEmpty should be(true)
        list.tail.forall(_.field.isEmpty) should be(false)
      }
    }

    //    describe("(when given an empty SolrQuery)") {
    //      val solrQuery = new SolrQuery
    //
    //      it("produces an EuropeanaQueryException") {
    //        evaluating {bcFactory.createList(solrQuery, Locale.CANADA)} should produce[EuropeanaQueryException]
    //      }
    //    }

    describe("(when given an illegal FilterQuery)") {
      val solrQuery: SolrQuery = new SolrQuery(queryString)
      val filterQueries = SolrQueryService.createFilterQueryList(Array("LANGUAGE:en", "wrong filter query"))

      it("should ignore the illegal FilterQuery") {
        val list: List[BreadCrumb] = (SolrQueryService.createBreadCrumbList(CHQuery(solrQuery = solrQuery, filterQueries = filterQueries)))
        list.size should equal(2)
        val lastBreadCrumb: BreadCrumb = list.last
        lastBreadCrumb.isLast should be(true)
        lastBreadCrumb.href should equal(queryPrefix + filterPrefix + "LANGUAGE:en")
      }
    }
  }
}