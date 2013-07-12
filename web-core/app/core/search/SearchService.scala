package core.search

import play.api.mvc._
import models.{ MetadataAccessors, OrganizationConfiguration }
import scala.collection.immutable.{ List, Map }
import controllers.ListItem

/**
 * Search Service abstraction.
 *
 * TODO this is directly refactored from the SOLR implementation, we should make it a little prettier when there's time for it.
 * E.g. not returning a PlainResult which is Play-specific
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait SearchService {

  def getApiResult(queryString: Map[String, Seq[String]], host: String, hiddenQueryFilters: Seq[String] = Seq.empty)(implicit configuration: OrganizationConfiguration): PlainResult

  def search(user: Option[String], hiddenQueryFilters: List[String], params: Map[String, Seq[String]], host: String)(implicit configuration: OrganizationConfiguration): (Seq[ListItem], SearchResult)

}

trait SearchResult {
  def getResultDocuments: List[MetadataAccessors] // TODO rename result type, move here, etc.
  def getFacetQueryLinks: List[FacetQueryLinks]
  def getPagination: ResultPagination
}

trait PresentationQuery {
  def getUserSubmittedQuery: String
  def getQueryForPresentation: String
  def getQueryToSave: String
  def getTypeQuery: String
  def getParsedQuery: String
}

trait ResultPagination {
  def isPrevious: Boolean
  def isNext: Boolean
  def getPreviousPage: Int
  def getNextPage: Int
  def getLastViewableRecord: Int
  def getNumFound: Int
  def getRows: Int
  def getStart: Int
  def getPageNumber: Int
  def getPageLinks: List[PageLink]
  def getBreadcrumbs: List[BreadCrumb]
  def getPresentationQuery: PresentationQuery
  def getLastViewablePage: Int
}

trait FacetQueryLinks {
  def getType: String
  def getLinks: List[FacetCountLink]
  def isFacetSelected: Boolean
  def getMissingValueCount: Int
}

trait FacetCountLink {
  def getValue: String
  def getCount: Long
}

case class FacetElement(facetName: String, facetInternationalisationCode: String, nrDisplayColumns: Int = 1)

case class SortElement(sortKey: String, sortAscending: Boolean = true)

case class PageLink(start: Int, display: Int, isLinked: Boolean = false) {
  override def toString: String = if (isLinked) "%i:%i".format(display, start) else display.toString
}

case class BreadCrumb(href: String, display: String, field: String = "", localisedField: String = "", value: String, isLast: Boolean = false) {
  override def toString: String = "<a href=\"" + href + "\">" + display + "</a>"
}