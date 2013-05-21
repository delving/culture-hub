package core

import models.OrganizationConfiguration

/**
 * Service that provides "searchIn" targets to the platform.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
trait SearchInService {

  /**
   * A map having as key the searchIn target and as value the internationalization key.
   */
  def getSearchInTargets(connectedUser: Option[String])(implicit configuration: OrganizationConfiguration): Map[String, String]

  /**
   * Composes the query based on the searchIn value and the query term
   */
  def composeSearchInQuery(searchIn: String, queryTerm: String)(implicit configuration: OrganizationConfiguration): Option[String]

}