package core.search

import core.SearchInService
import models.OrganizationConfiguration

/**
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
class BasicSearchInService extends SearchInService {
  /**
   * A map having as key the searchIn target and as value the internationalization key.
   */
  def getSearchInTargets(connectedUser: Option[String])(implicit configuration: OrganizationConfiguration): Map[String, String] = configuration.searchService.searchIn

  /**
   * Composes the query based on the searchIn value and the query term
   */
  def composeSearchInQuery(searchIn: String, queryTerm: String)(implicit configuration: OrganizationConfiguration): Option[String] = {
    if (configuration.searchService.searchIn.contains(searchIn)) {
      Some("""%s:"%s"""".format(searchIn, queryTerm))
    } else {
      None
    }
  }
}