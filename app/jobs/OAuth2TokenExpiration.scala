package jobs

import play.jobs.{Every, On, Job}
import controllers.OAuth2TokenEndpoint

/**
 * 
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
@Every("5min")
class OAuth2TokenExpiration extends Job {
  override def doJob() {
    OAuth2TokenEndpoint.evictExpiredTokens()
  }
}