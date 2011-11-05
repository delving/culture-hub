package controllers.custom

import play.mvc.results.RenderXml
import play.mvc.Controller

/**
 *
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since 11/5/11 10:35 AM  
 */

object ItinEndPoint extends Controller {

  import play.mvc.results.Result

  def search: Result = {
    new RenderXml("<bla>rocks</bla>")
  }

  def store(data: String): Result = {
    import models.DrupalEntity
    val response = DrupalEntity.processStoreRequest(data)((item, list) => println(item.toSolrDocument, list))
    val responseString =
    <response recordsProcessed={response.itemsParsed.toString} linksProcessed={response.coRefsParsed.toString}>
      <status>{if (response.success) "succcess" else "failure"}</status>
      {if (!response.errorMessage.isEmpty) <error> {response.errorMessage}</error>}
    </response>
    new RenderXml(responseString)
  }

  // todo implement this
  def sync(data: String): Result = {

    new RenderXml("<bla>rocks</bla>")
  }

}