package controllers

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 7/5/11 8:07 AM  
 */

trait HTTPClient {

  import org.apache.commons.httpclient.params.HttpConnectionManagerParams
  import org.apache.commons.httpclient.{HttpClient, MultiThreadedHttpConnectionManager}

  val connectionParams = new HttpConnectionManagerParams
  connectionParams setDefaultMaxConnectionsPerHost (15)
  connectionParams setMaxTotalConnections (250)
  connectionParams setConnectionTimeout (2000)
  val multiThreadedHttpConnectionManager = new MultiThreadedHttpConnectionManager()
  multiThreadedHttpConnectionManager setParams (connectionParams)

  def getHttpClient : HttpClient = new HttpClient(multiThreadedHttpConnectionManager)
}