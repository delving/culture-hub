package core.search

import core.SearchInService
import models.DomainConfiguration

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class BasicSearchInService extends SearchInService {
  /**
   * A map having as key the searchIn target and as value the internationalization key.
   */
  def getSearchInTargets(connectedUser: Option[String])(implicit configuration: DomainConfiguration): Map[String, String] = configuration.searchService.searchIn

  /**
   * Composes the query based on the searchIn value and the query term
   */
  def composeSearchInQuery(searchIn: String, queryTerm: String)(implicit configuration: DomainConfiguration): Option[String] = {
    if (configuration.searchService.searchIn.contains(searchIn)) {
      Some("""%s:"%s"""".format(searchIn, queryTerm))
    } else {
      None
    }
  }
}
