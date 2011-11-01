package search

/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.1 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

import _root_.java.lang.String
import _root_.org.apache.solr.client.solrj.response.FacetField
import _root_.org.apache.solr.client.solrj.SolrQuery
import _root_.org.junit.runner.RunWith
import _root_.org.scalatest.junit.JUnitRunner
import _root_.org.scalatest.matchers.ShouldMatchers
import collection.mutable.ListBuffer
import collection.immutable.List
import play.test.UnitSpec

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since Apr 5, 2010 10:55:58 PM
 */

class FacetQueryLinksSpec extends UnitSpec with ShouldMatchers {

  import controllers.search.{FilterQuery, FacetQueryLinks, FacetCountLink}

//  describe("A List of FacetQueryLinks") {
//
//    describe("(when given a SolrQuery with filterQueries and a List of FacetFields)") {
//      import controllers.search.{CHResponse, SolrQueryService}
//      val (solrQuery, facets, rawfilterQueries, filterQueries) = createQueryAndFacets
//      val facetLinks = SolrQueryService.createFacetQueryLinks(CHResponse())
//
//      it("should have all FacetFields that have filter queries selected") {
//        facetLinks.foreach {
//          facetLink => facetLink.getType match {
//            case "TYPE" =>
//              facetLink.isFacetSelected should be(false)
//              facetLink.getLinks.size should be(0)
//            case _ =>
//              facetLink.isFacetSelected should be(true)
//              facetLink.getLinks.size should not be (0)
//          }
//        }
//      }
//
//      it("should have isRemove as true for every selected FacetCountLink") {
//        facetLinks.foreach {
//          facetLink =>
//            facetLink.getLinks.foreach {
//              facetCountLink =>
//                facetCountLink.remove should equal(filterQueries contains currentFilterQuery(facetLink, facetCountLink))
//            }
//        }
//      }
//
//      it("should not contain the selected queryFilter in the url when isRemove is true") {
//        facetLinks.foreach {
//          facetLink =>
//            facetLink.getLinks.foreach {
//              facetCountLink =>
//                facetCountLink.remove should not equal (facetCountLink.url contains currentFilterQuery(facetLink, facetCountLink))
//            }
//        }
//      }
//
//      it("should contain all the filterQueries in the url when isRemove is false") {
//        facetLinks.foreach {
//          facetLink =>
//            facetLink.getLinks.filter(_.remove == false).foreach {
//              facetCountLink =>
//                {
//                  val appliedQueryFilters = getAppliedQueryFilters(facetCountLink)
//                  appliedQueryFilters.dropRight(1) should equal(filterQueries)
//                  appliedQueryFilters.reverse.head should equal(currentFilterQuery(facetLink, facetCountLink))
//                }
//            }
//        }
//      }
//
//      it("should not contain YEAR facet entries that contain '0000") {
//        facetLinks.filter(_.getType == "YEAR").head.getLinks.exists(_.value == "0000") should be(false)
//      }
//
//    }
//
//    describe("(when given a SolrQuery with FilterQueries but with an empty facetList)") {
//      val solrQuery = new SolrQuery("query").addFilterQuery("YEAR:1977")
//      val emptyFacetList = new ListBuffer[FacetField]
//
//      it("should return an empty list") {
//        import controllers.search.SolrQueryService
//        val facetLinks = SolrQueryService.createFacetQueryLinks(solrQuery, §emptyFacetList)
//        facetLinks should be('empty)
//      }
//    }
//
//    describe("(when given a SolrQuery without FilterQueries)") {
//      import controllers.search.SolrQueryService
//      val (solrQuery, facets, rawFilterQueries, filterQueries) = createQueryAndFacets
//      rawFilterQueries.foreach(fq => solrQuery.removeFilterQuery(fq))
//      val facetLinks = SolrQueryService.createFacetQueryLinks(solrQuery, facets)
//
//      it("should not contain any select FacetFields") {
//        facetLinks.forall(_.isFacetSelected) should be(false)
//      }
//
//      it("should have all isRemove as false") {
//        facetLinks.foreach(_.getLinks.foreach(_.remove should be(false)))
//      }
//
//      it("should only have the current Facet in the url") {
//        facetLinks.foreach(
//          facetLink =>
//            facetLink.getLinks.foreach(
//              facetCountLink =>
//                {
//                  val appliedFilters: List[String] = getAppliedQueryFilters(facetCountLink)
//                  appliedFilters.size should be(1)
//                  appliedFilters.head should equal(currentFilterQuery(facetLink, facetCountLink))
//                }
//              )
//          )
//      }
//
//    }
//  }
//
//  def getAppliedQueryFilters(facetCountLink: FacetCountLink): List[String] = facetCountLink.url.split("&qf=").toList.tail
//
//  def currentFilterQuery(facetLink: FacetQueryLinks, facetCountLink: FacetCountLink): String = facetLink.getType + ":" + facetCountLink.value
//
//  def createQueryAndFacets: (SolrQuery, ListBuffer[FacetField], ListBuffer[String], List[FilterQuery]) = {
//    import controllers.search.SolrQueryService
//    val rawFilterQueries = new ListBuffer[String] += ("LANGUAGE:de", "LANGUAGE:nl", "YEAR:1980")
//    rawFilterQueries foreach (solrQuery addFacetField  _)
//    val filterQueries = SolrQueryService.createFilterQueryList(rawFilterQueries.toArray)
//
//    val solrQuery = new SolrQuery("everything")
//            .addFacetField("LANGUAGE", "YEAR", "TYPE")
//            .addFilterQuery(rawFilterQueries: _*)
//
//    val languageFacetEntries = Map("en" -> 1, "de" -> 2, "nl" -> 3)
//    val languageFacet: FacetField = new FacetField("LANGUAGE")
//    languageFacetEntries.foreach(entry => languageFacet add (entry._1, entry._2))
//
//    val yearFacetEntries = Map("0000" -> 666, "1980" -> 1, "1981" -> 2, "1982" -> 3)
//    val yearFacet = new FacetField("YEAR")
//    yearFacetEntries.foreach(entry => yearFacet add (entry._1, entry._2))
//
//    val typeFacet = new FacetField("TYPE")
//    val facets = new ListBuffer[FacetField] += (languageFacet, yearFacet, typeFacet)
//    (solrQuery, facets, rawFilterQueries, filterQueries)
//  }

}